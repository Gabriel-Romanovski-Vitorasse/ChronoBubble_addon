package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Renascimento" do Julius: quem usa magia de Tempo e morre volta com o grim\u00f3rio de uma p\u00e1gina.
 * Enquanto o Tempo estiver abaixo de UNLOCK_TIME: o Tempo s\u00f3 sobe roubando (sem regenera\u00e7\u00e3o passiva) e
 * Chrono Stasis / Chrono Stasis Grigora ficam bloqueados. Ao chegar l\u00e1, tudo \u00e9 devolvido.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RebirthHandler {
    // ===== Ajustes f\u00e1ceis =====
    public static boolean ENABLED = true;
    /** Tempo necess\u00e1rio para o grim\u00f3rio e os poderes voltarem ao normal. */
    public static int UNLOCK_TIME = 200;
    /** Tempo com que o jogador renasce. */
    public static int REBORN_TIME = 0;
    /** Bloqueia a regenera\u00e7\u00e3o passiva de +1 por segundo enquanto estiver renascido (s\u00f3 roubar tempo conta). */
    public static boolean BLOCK_PASSIVE_REGEN = true;
    /** Ao morrer, o Swallowtail no invent\u00e1rio gasta Time guardado para pular ou reduzir o renascimento. */
    public static boolean SWALLOWTAIL_RESERVE = true;
    /** Quanto de Time \u00e9 tirado do Swallowtail ao morrer (se cobrir UNLOCK_TIME, o renascimento \u00e9 pulado). */
    public static int RESERVE_COST = 200;

    private static final String TAG_REBORN = "chrono_bubble_reborn";
    private static final String TAG_PENDING = "chrono_bubble_pending";
    private static final String TAG_REMOVED = "chrono_bubble_removed";
    private static final String TAG_RESERVE = "chrono_bubble_reserve";

    private static final Map<UUID, Integer> LAST_TIME = new HashMap<>();

    // ---------- Morte e respawn ----------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!ENABLED || !(event.getEntityLiving() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();
        if (BlockCloverBridge.isTimeUser(player)) {
            player.getPersistentData().putBoolean(TAG_PENDING, true);
            // o Swallowtail gasta Time guardado agora, antes de o invent\u00e1rio cair no ch\u00e3o
            int used = SWALLOWTAIL_RESERVE ? SwallowtailItem.consumeReserve(player, RESERVE_COST) : 0;
            player.getPersistentData().putInt(TAG_RESERVE, used);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundNBT from = event.getOriginal().getPersistentData();
        CompoundNBT to = event.getPlayer().getPersistentData();
        to.putBoolean(TAG_REBORN, from.getBoolean(TAG_REBORN));
        to.putBoolean(TAG_PENDING, from.getBoolean(TAG_PENDING));
        to.putString(TAG_REMOVED, from.getString(TAG_REMOVED));
        to.putInt(TAG_RESERVE, from.getInt(TAG_RESERVE));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        broadcastState(event.getPlayer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        broadcastState(event.getPlayer());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        broadcastState(event.getPlayer());
    }

    /** Quando um jogador passa a ver outro, avisa se o outro est\u00e1 com o grim\u00f3rio de uma p\u00e1gina. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity) || !(event.getTarget() instanceof PlayerEntity)) return;
        PlayerEntity target = (PlayerEntity) event.getTarget();
        if (target.getPersistentData().getBoolean(TAG_REBORN)) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()),
                    new RebirthPacket(target.getId(), true));
        }
    }

    // ---------- Tick do servidor ----------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        CompoundNBT data = player.getPersistentData();

        if (data.getBoolean(TAG_PENDING) && player.isAlive() && player.tickCount > 20) {
            applyRebirth(player);
        }
        if (!data.getBoolean(TAG_REBORN)) return;

        int time = BlockCloverBridge.getTime(player);
        if (time < 0) return;
        if (time >= UNLOCK_TIME) {
            unlock(player);
            return;
        }

        // Bloqueia a regenera\u00e7\u00e3o passiva (+1/s, que o mod aplica a cada segundo)
        UUID id = player.getUUID();
        Integer last = LAST_TIME.get(id);
        if (BLOCK_PASSIVE_REGEN && last != null && player.tickCount % 20 == 0) {
            int delta = time - last;
            if (delta >= 1 && delta <= 2) {
                BlockCloverBridge.setTime(player, last);
                time = last;
            }
        }
        LAST_TIME.put(id, time);

        // Se o jogador reaprender os poderes bloqueados (menus do mod), tira de novo
        if (player.tickCount % 20 == 10) {
            addRemoved(data, BlockCloverBridge.lockStasisAbilities(player));
        }
    }

    // ---------- Estados ----------

    private static void applyRebirth(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.putBoolean(TAG_PENDING, false);
        int reserve = data.getInt(TAG_RESERVE);
        data.putInt(TAG_RESERVE, 0);

        // Swallowtail cobriu o pre\u00e7o inteiro: o renascimento \u00e9 pulado, o grim\u00f3rio continua completo
        if (reserve > 0 && reserve >= UNLOCK_TIME) {
            BlockCloverBridge.setTime(player, REBORN_TIME);
            BlockCloverBridge.syncToClient(player);
            player.displayClientMessage(new StringTextComponent(
                    "\u00a7bO Swallowtail gastou " + reserve + " de Time e preservou o seu grim\u00f3rio."), true);
            return;
        }

        data.putBoolean(TAG_REBORN, true);
        int startTime = REBORN_TIME + Math.max(0, reserve);
        BlockCloverBridge.setTime(player, startTime);
        LAST_TIME.put(player.getUUID(), startTime);
        addRemoved(data, BlockCloverBridge.lockStasisAbilities(player));
        BlockCloverBridge.syncToClient(player);
        broadcastState(player);
        player.displayClientMessage(new StringTextComponent(
                "\u00a7bSeu grim\u00f3rio foi reduzido a uma p\u00e1gina. Roube tempo at\u00e9 " + UNLOCK_TIME + " para restaur\u00e1-lo."
                + (reserve > 0 ? " \u00a77(Swallowtail: +" + reserve + " de Time)" : "")), true);
    }

    private static void unlock(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        String stored = data.getString(TAG_REMOVED);
        List<String> keys = stored.isEmpty() ? new ArrayList<String>() : Arrays.asList(stored.split(","));
        BlockCloverBridge.restoreAbilities(player, keys);
        data.putBoolean(TAG_REBORN, false);
        data.putString(TAG_REMOVED, "");
        LAST_TIME.remove(player.getUUID());
        BlockCloverBridge.syncToClient(player);
        broadcastState(player);
        player.level.playSound(null, player.getX(), player.getY() + 1.0D, player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(new StringTextComponent(
                "\u00a7bSeu grim\u00f3rio foi restaurado. Equipe novamente seus poderes de tempo."), true);
    }

    private static void addRemoved(CompoundNBT data, List<String> newlyRemoved) {
        if (newlyRemoved.isEmpty()) return;
        List<String> all = new ArrayList<>();
        String stored = data.getString(TAG_REMOVED);
        if (!stored.isEmpty()) all.addAll(Arrays.asList(stored.split(",")));
        for (String k : newlyRemoved) if (!all.contains(k)) all.add(k);
        data.putString(TAG_REMOVED, String.join(",", all));
    }

    /** Avisa o pr\u00f3prio jogador e quem o v\u00ea sobre o estado atual (grim\u00f3rio de uma p\u00e1gina ou normal). */
    private static void broadcastState(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity)) return;
        boolean reborn = player.getPersistentData().getBoolean(TAG_REBORN);
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new RebirthPacket(player.getId(), reborn));
    }
}
