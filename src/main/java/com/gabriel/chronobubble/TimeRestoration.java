package com.gabriel.chronobubble;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ExperienceOrbEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Restauração do tempo (Shift + Time Healing): faz voltar os blocos quebrados ou explodidos há pouco, em volta do
 * jogador, do mais recente para o mais antigo, aos poucos (como rebobinar).
 * <p>
 * <b>Sem duplicar itens.</b> Cada quebra (ou explosão inteira) vira um "grupo" com os itens que derrubou. Um grupo só
 * volta se esses itens ainda estiverem no chão por perto, e eles somem quando o grupo volta; se alguém já pegou, o
 * grupo não volta. Ficam de fora: blocos que soltam XP (minérios), blocos com conteúdo (baús, fornalhas...) e
 * blocos de duas partes (portas, camas, plantas altas).
 * <p>
 * Limitação conhecida: blocos que caem sozinhos quando o de baixo some (tochas, placas, areia) não são registrados.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeRestoration {
    /** Limite de blocos guardados por dimensão (os mais antigos saem primeiro). */
    private static final int MAX_HISTORY_BLOCKS = 4096;
    /** Folga, em blocos, para procurar os itens derrubados em volta do grupo (eles rolam um pouco). */
    private static final double DROP_SEARCH = 3.0D;

    /** Uma quebra ou uma explosão: blocos (posição + estado) e os itens que derrubou. */
    static final class Group {
        final long tick;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        final List<ItemStack> drops = new ArrayList<>();
        /** Soltou XP (minério): não dá para voltar sem duplicar a experiência. */
        boolean blocked;

        Group(long tick) { this.tick = tick; }

        int size() { return positions.size(); }

        boolean near(BlockPos center, double radius) {
            double r2 = radius * radius;
            for (BlockPos p : positions) if (p.distSqr(center) <= r2) return true;
            return false;
        }

        AxisAlignedBB bounds() {
            AxisAlignedBB box = new AxisAlignedBB(positions.get(0));
            for (BlockPos p : positions) box = box.minmax(new AxisAlignedBB(p));
            return box.inflate(DROP_SEARCH);
        }
    }

    /** Histórico por dimensão, do mais antigo (início) para o mais recente (fim). */
    private static final Map<RegistryKey<World>, Deque<Group>> HISTORY = new HashMap<>();
    /** Grupos criados neste tick, por posição: liga os itens que aparecem logo depois à quebra que os soltou. */
    private static final Map<RegistryKey<World>, Map<Long, Group>> THIS_TICK = new HashMap<>();
    private static long thisTick = Long.MIN_VALUE;

    /** Restaurações em andamento (os blocos voltam alguns por tick). */
    private static final class Job {
        final ServerWorld world;
        final List<BlockPos> positions = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        final BlockPos center;
        int next;

        Job(ServerWorld world, BlockPos center) { this.world = world; this.center = center; }
    }

    private static final List<Job> JOBS = new ArrayList<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    // ---------- registro do que foi destruído ----------

    /** Blocos que dá para voltar com segurança (sem conteúdo e sem outra metade). */
    static boolean restorable(BlockState state) {
        if (state.isAir() || state.hasTileEntity()) return false;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) return false;
        return !state.getMaterial().isLiquid();                              // \u00e1gua/lava n\u00e3o se "quebra"
    }

    private static Map<Long, Group> tickIndex(World world) {
        long now = world.getGameTime();
        if (now != thisTick) {
            THIS_TICK.clear();
            thisTick = now;
        }
        return THIS_TICK.computeIfAbsent(world.dimension(), k -> new HashMap<>());
    }

    private static void remember(World world, Group g) {
        if (g.size() == 0) return;
        Deque<Group> h = HISTORY.computeIfAbsent(world.dimension(), k -> new ArrayDeque<>());
        h.addLast(g);
        Map<Long, Group> idx = tickIndex(world);
        for (BlockPos p : g.positions) idx.put(p.asLong(), g);
        int total = 0;
        for (Group x : h) total += x.size();
        while (total > MAX_HISTORY_BLOCKS && h.size() > 1) total -= h.removeFirst().size();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getWorld() instanceof ServerWorld) || !ChronoConfig.REST_ENABLED.get()) return;
        ServerWorld world = (ServerWorld) event.getWorld();
        if (!restorable(event.getState())) return;
        Group g = new Group(world.getGameTime());
        g.positions.add(event.getPos().immutable());
        g.states.add(event.getState());
        remember(world, g);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isClientSide || !ChronoConfig.REST_ENABLED.get()) return;
        Group g = new Group(world.getGameTime());
        for (BlockPos p : event.getAffectedBlocks()) {
            BlockState state = world.getBlockState(p);
            if (state.isAir()) continue;
            if (!restorable(state)) { g.blocked = true; continue; }          // ex.: baú na explosão: não volta parcial
            g.positions.add(p.immutable());
            g.states.add(state);
        }
        remember(world, g);
    }

    /** Itens e XP que aparecem no mesmo tick e na mesma posição de uma quebra são dela. */
    @SubscribeEvent
    public static void onDrop(EntityJoinWorldEvent event) {
        World world = event.getWorld();
        if (world.isClientSide) return;
        Entity e = event.getEntity();
        if (!(e instanceof ItemEntity) && !(e instanceof ExperienceOrbEntity)) return;
        if (world.getGameTime() != thisTick) return;
        Map<Long, Group> idx = THIS_TICK.get(world.dimension());
        if (idx == null) return;
        Group g = idx.get(e.blockPosition().asLong());
        if (g == null) return;
        if (e instanceof ItemEntity) g.drops.add(((ItemEntity) e).getItem().copy());
        else g.blocked = true;
    }

    // ---------- uso ----------

    /** Chamado no lugar do Time Healing original (que é cancelado) quando o jogador segura Shift. */
    public static void start(ServerPlayerEntity player) {
        if (!ChronoConfig.REST_ENABLED.get()) return;
        ServerWorld world = player.getLevel();
        long now = world.getGameTime();
        Long ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            Msg.bar(player, TextFormatting.GRAY, "restore_cooldown", Msg.f1((ready - now) / 20.0));
            return;
        }
        if (ChronoConfig.REST_REQUIRE_COMBAT.get() && !BlockCloverBridge.isInCombat(player)) {
            Msg.bar(player, TextFormatting.RED, "need_combat");
            return;
        }
        if (!(BlockCloverBridge.getMana(player) > ChronoConfig.f(ChronoConfig.REST_MANA_COST))) {
            Msg.bar(player, TextFormatting.RED, "no_mana");
            return;
        }
        int base = ChronoConfig.REST_TIME_COST.get(), perBlock = ChronoConfig.REST_TIME_PER_BLOCK.get();
        int budget = RestorationPlanner.affordableBlocks(BlockCloverBridge.getTime(player), base, perBlock,
                ChronoConfig.REST_MAX_BLOCKS.get());
        if (budget <= 0) {
            Msg.bar(player, TextFormatting.RED, "no_time");
            return;
        }

        // candidatos: recentes e perto, do mais recente para o mais antigo
        Deque<Group> history = HISTORY.get(world.dimension());
        List<Group> candidates = new ArrayList<>();
        long oldest = now - ChronoConfig.REST_WINDOW_SECONDS.get() * 20L;
        BlockPos center = player.blockPosition();
        double radius = ChronoConfig.REST_RADIUS.get();
        if (history != null) {
            for (Iterator<Group> it = history.descendingIterator(); it.hasNext(); ) {
                Group g = it.next();
                if (g.tick < oldest) break;
                if (g.near(center, radius)) candidates.add(g);
            }
        }
        if (candidates.isEmpty()) {
            Msg.bar(player, TextFormatting.GRAY, "restore_nothing");
            return;
        }

        int[] missingDrops = { 0 };
        List<Group> chosen = RestorationPlanner.pick(candidates, Group::size, g -> {
            if (g.blocked || !placeFree(world, g)) return false;
            if (!takeDrops(world, g, false)) { missingDrops[0] += g.size(); return false; }
            return true;
        }, budget);
        if (chosen.isEmpty()) {
            Msg.bar(player, TextFormatting.GRAY, missingDrops[0] > 0 ? "restore_drops_taken" : "restore_nothing");
            return;
        }

        Job job = new Job(world, center);
        int blocks = 0;
        for (Group g : chosen) {
            takeDrops(world, g, true);                                    // os itens derrubados somem: o bloco volta
            job.positions.addAll(g.positions);
            job.states.addAll(g.states);
            blocks += g.size();
            history.remove(g);
        }
        sortBottomUp(job);
        JOBS.add(job);

        int timeCost = base + perBlock * blocks;
        BlockCloverBridge.spend(player, ChronoConfig.f(ChronoConfig.REST_MANA_COST), timeCost, 0);
        BlockCloverBridge.syncToClient(player);
        READY_AT.put(player.getUUID(), now + ChronoConfig.REST_COOLDOWN_TICKS.get());
        world.playSound(null, player.getX(), player.getY(), player.getZ(), ChronoBubbleMod.STASIS_START.get(),
                SoundCategory.PLAYERS, 1.0F, 0.6F);
        Msg.bar(player, TextFormatting.AQUA, "restore_on", blocks, timeCost);
        if (missingDrops[0] > 0) Msg.chat(player, TextFormatting.GRAY, "restore_some_taken", missingDrops[0]);
    }

    /** Todas as posições do grupo estão livres (ar, água, grama alta...)? Senão não volta (não apaga o que foi posto). */
    private static boolean placeFree(ServerWorld world, Group g) {
        for (BlockPos p : g.positions) {
            if (!world.isLoaded(p)) return false;
            BlockState now = world.getBlockState(p);
            if (!now.isAir() && !now.getMaterial().isReplaceable()) return false;
        }
        return true;
    }

    /**
     * Os itens que o grupo derrubou ainda estão no chão por perto? Com {@code consume}, tira esses itens do mundo.
     * Conta por tipo de item somando todas as pilhas (itens iguais se juntam no chão).
     */
    private static boolean takeDrops(ServerWorld world, Group g, boolean consume) {
        if (g.drops.isEmpty()) return true;
        List<ItemEntity> around = world.getEntitiesOfClass(ItemEntity.class, g.bounds(), ItemEntity::isAlive);
        List<ItemStack> need = new ArrayList<>();
        for (ItemStack d : g.drops) {
            boolean merged = false;
            for (ItemStack n : need) {
                if (same(n, d)) { n.grow(d.getCount()); merged = true; break; }
            }
            if (!merged) need.add(d.copy());
        }
        for (ItemStack n : need) {
            int have = 0;
            for (ItemEntity ie : around) if (same(ie.getItem(), n)) have += ie.getItem().getCount();
            if (have < n.getCount()) return false;
        }
        if (!consume) return true;
        for (ItemStack n : need) {
            int left = n.getCount();
            for (ItemEntity ie : around) {
                if (left <= 0) break;
                ItemStack s = ie.getItem();
                if (!same(s, n)) continue;
                int take = Math.min(left, s.getCount());
                left -= take;
                if (take >= s.getCount()) ie.remove();
                else {
                    ItemStack rest = s.copy();
                    rest.shrink(take);
                    ie.setItem(rest);
                }
            }
        }
        return true;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSame(a, b) && ItemStack.tagMatches(a, b);
    }

    /** De baixo para cima (o que precisa de apoio volta depois do apoio). */
    private static void sortBottomUp(Job job) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < job.positions.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer i) -> job.positions.get(i).getY())
                .thenComparingDouble(i -> job.positions.get(i).distSqr(job.center)));
        List<BlockPos> ps = new ArrayList<>();
        List<BlockState> ss = new ArrayList<>();
        for (int i : order) { ps.add(job.positions.get(i)); ss.add(job.states.get(i)); }
        job.positions.clear(); job.positions.addAll(ps);
        job.states.clear(); job.states.addAll(ss);
    }

    // ---------- animação e limpeza ----------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
            Job job = it.next();
            int perTick = ChronoConfig.REST_BLOCKS_PER_TICK.get();
            for (int k = 0; k < perTick && job.next < job.positions.size(); k++, job.next++) {
                place(job.world, job.positions.get(job.next), job.states.get(job.next), true);
            }
            if (job.next >= job.positions.size()) {
                job.world.playSound(null, job.center, ChronoBubbleMod.STASIS_END.get(), SoundCategory.PLAYERS, 1.0F, 1.2F);
                it.remove();
            }
        }
        pruneOld();
    }

    private static void place(ServerWorld world, BlockPos pos, BlockState state, boolean effects) {
        if (!world.isLoaded(pos)) return;
        BlockState now = world.getBlockState(pos);
        if (!now.isAir() && !now.getMaterial().isReplaceable()) return;    // alguém pôs algo ali no meio tempo
        world.setBlock(pos, state, 3);
        if (!effects) return;
        world.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                6, 0.3D, 0.3D, 0.3D, 0.02D);
        if (world.random.nextInt(4) == 0) {
            world.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundCategory.BLOCKS, 0.6F, 1.3F);
        }
    }

    /** Esquece o que ficou velho demais para voltar. */
    private static void pruneOld() {
        long window = ChronoConfig.REST_WINDOW_SECONDS.get() * 20L;
        for (Deque<Group> h : HISTORY.values()) {
            if (h.isEmpty()) continue;
            long newest = h.peekLast().tick;
            while (!h.isEmpty() && newest - h.peekFirst().tick > window + 200) h.removeFirst();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChronoThrowHandler.forgetExpired(READY_AT, event.getPlayer().getUUID(), event.getPlayer().level.getGameTime());
    }

    /** O servidor vai fechar: termina as restaurações na hora (os itens já foram tirados) e esquece o histórico. */
    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        for (Job job : JOBS) {
            for (; job.next < job.positions.size(); job.next++) place(job.world, job.positions.get(job.next), job.states.get(job.next), false);
        }
        JOBS.clear();
        HISTORY.clear();
        THIS_TICK.clear();
        thisTick = Long.MIN_VALUE;
    }
}
