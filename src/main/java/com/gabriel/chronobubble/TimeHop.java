package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SPlayerPositionLookPacket;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time Hop reformulado: ao usar, o tempo ao redor PARA (esfera de tempo, mobs e proj\u00e9teis congelados), voc\u00ea
 * desliza at\u00e9 o destino dentro dela e, quando chega, o tempo volta a correr. Substitui o teletransporte
 * instant\u00e2neo do Block Clover (custos e dist\u00e2ncia equivalentes, ajust\u00e1veis).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeHop {
    // ===== Ajustes f\u00e1ceis (o .toml e a tela de configura\u00e7\u00e3o t\u00eam prioridade) =====
    public static boolean ENABLED = true;
    public static float MANA_COST = 20.0F;
    public static int TIME_COST = 20;
    /** Dist\u00e2ncia m\u00e1xima do salto, em blocos (normal e agachado). */
    public static float DISTANCE = 8.0F;
    public static float DISTANCE_SNEAK = 20.0F;
    /** Quantos ticks dura o deslize at\u00e9 o destino. */
    public static int DASH_TICKS = 6;
    /** Quanto tempo o tempo continua parado depois que voc\u00ea chega. */
    public static int LINGER_TICKS = 14;
    public static int COOLDOWN_TICKS = 40;
    public static boolean FREEZE_ENEMIES = true;
    public static boolean FREEZE_PLAYERS = false;
    public static boolean SKIP_BOSSES = true;

    private static final ResourceLocation EFFECT_ID = new ResourceLocation("block_clover", "chrono_stasis");
    private static final String TAG_SUSTAINED = "chrono_bubble_sustained";

    private static final class Hop {
        final Vector3d from, to;
        final List<LivingEntity> frozen = new ArrayList<>();
        int tick;
        Hop(Vector3d from, Vector3d to) { this.from = from; this.to = to; }
    }

    private static final Map<UUID, Hop> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public static boolean isActive(PlayerEntity p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Chamado no lugar do Time Hop original (que \u00e9 cancelado). */
    public static void start(ServerPlayerEntity player) {
        if (!ENABLED || ACTIVE.containsKey(player.getUUID())) return;
        long now = player.level.getGameTime();
        Long ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            player.displayClientMessage(new StringTextComponent("\u00a77Time Hop em recarga: "
                    + String.format("%.1fs", (ready - now) / 20.0)), true);
            return;
        }
        if (!(BlockCloverBridge.getMana(player) >= MANA_COST) || !(BlockCloverBridge.getTime(player) > TIME_COST)) {
            player.displayClientMessage(new StringTextComponent("\u00a7cMana ou Time insuficiente."), true);
            return;
        }
        Vector3d dest = findDestination(player);
        if (dest == null) {
            player.displayClientMessage(new StringTextComponent("\u00a77Sem espa\u00e7o para saltar nessa dire\u00e7\u00e3o."), true);
            return;
        }

        BlockCloverBridge.spend(player, MANA_COST, TIME_COST, 0);
        BlockCloverBridge.syncToClient(player);
        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS + DASH_TICKS + LINGER_TICKS);

        Hop hop = new Hop(player.position(), dest);
        ACTIVE.put(player.getUUID(), hop);
        freezeAround(player, hop);

        ChronoZone.send(player, true);          // esfera de tempo parado em volta de voc\u00ea
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 1.2F, 0.6F);
    }

    /** Destino do salto: segue para onde voc\u00ea olha, para antes de blocos e procura um lugar livre. */
    private static Vector3d findDestination(ServerPlayerEntity p) {
        double max = p.isShiftKeyDown() ? DISTANCE_SNEAK : DISTANCE;
        Vector3d eye = p.getEyePosition(1.0F);
        Vector3d look = p.getLookAngle();
        BlockRayTraceResult hit = p.level.clip(new RayTraceContext(eye, eye.add(look.scale(max)),
                RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, p));
        double d = hit.getType() == RayTraceResult.Type.MISS ? max : Math.max(0.0D, eye.distanceTo(hit.getLocation()) - 0.5D);
        for (double t = d; t >= 1.0D; t -= 0.5D) {
            Vector3d pos = p.position().add(look.scale(t));
            AxisAlignedBB bb = p.getBoundingBox().move(pos.subtract(p.position()));
            if (p.level.noCollision(p, bb)) return pos;
        }
        return null;
    }

    private static double distToSegment(Vector3d a, Vector3d b, Vector3d p) {
        Vector3d ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-6D ? 0.0D : Math.max(0.0D, Math.min(1.0D, p.subtract(a).dot(ab) / len2));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    private static void freezeAround(ServerPlayerEntity player, Hop hop) {
        if (!FREEZE_ENEMIES) return;
        Effect effect = ForgeRegistries.POTIONS.getValue(EFFECT_ID);
        if (effect == null) return;
        final double r = ChronoZone.RADIUS;
        AxisAlignedBB box = new AxisAlignedBB(hop.from, hop.to).inflate(r);
        int duration = DASH_TICKS + LINGER_TICKS + 2;
        for (LivingEntity e : player.level.getEntitiesOfClass(LivingEntity.class, box, x -> x != player && x.isAlive())) {
            if (e instanceof PlayerEntity && !FREEZE_PLAYERS) continue;
            if (SKIP_BOSSES && (e instanceof EnderDragonEntity || e instanceof WitherEntity)) continue;
            if (distToSegment(hop.from, hop.to, e.position()) > r) continue;
            if (e.hasEffect(effect)) continue;          // j\u00e1 preso por outro efeito: n\u00e3o mexe
            e.addEffect(new EffectInstance(effect, duration));
            hop.frozen.add(e);
        }
    }

    private static void finish(PlayerEntity player, Hop hop) {
        Effect effect = ForgeRegistries.POTIONS.getValue(EFFECT_ID);
        for (LivingEntity e : hop.frozen) {
            if (effect != null && e.isAlive() && e.hasEffect(effect) && !e.getPersistentData().getBoolean(TAG_SUSTAINED)) {
                e.removeEffect(effect);
            }
        }
        if (!ChronoZone.hasZone(player)) ChronoZone.send(player, false);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_END.get(), SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        Hop hop = ACTIVE.get(player.getUUID());
        if (hop == null) return;

        if (!player.isAlive()) { ACTIVE.remove(player.getUUID()); finish(player, hop); return; }
        hop.tick++;

        if (hop.tick <= DASH_TICKS) {
            double f = hop.tick / (double) DASH_TICKS;
            f = f * f * (3.0D - 2.0D * f);                              // come\u00e7a e termina suave
            Vector3d pos = hop.from.add(hop.to.subtract(hop.from).scale(f));
            player.connection.teleport(pos.x, pos.y, pos.z, 0.0F, 0.0F,
                    EnumSet.of(SPlayerPositionLookPacket.Flags.X_ROT, SPlayerPositionLookPacket.Flags.Y_ROT));
            player.fallDistance = 0.0F;
            if (player.level instanceof ServerWorld) {
                ((ServerWorld) player.level).sendParticles(ParticleTypes.END_ROD,
                        pos.x, pos.y + 1.0D, pos.z, 4, 0.2D, 0.5D, 0.2D, 0.02D);
            }
        } else if (hop.tick >= DASH_TICKS + LINGER_TICKS) {
            ACTIVE.remove(player.getUUID());
            finish(player, hop);
        }
    }

    private static void abort(UUID id, PlayerEntity player) {
        Hop hop = ACTIVE.remove(id);
        if (hop != null && player != null) finish(player, hop);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity) {
            abort(event.getEntityLiving().getUUID(), (PlayerEntity) event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        abort(event.getPlayer().getUUID(), event.getPlayer());
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
