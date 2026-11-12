package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
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
 * "Renascimento" do Julius: quem usa magia de Tempo e morre com Time guardado na Swallowtail volta com o grim\u00f3rio de
 * uma p\u00e1gina. Sem Time na Swallowtail n\u00e3o h\u00e1 renascimento: a morte \u00e9 uma morte comum.
 * Ao renascer, tudo zera: o Time do jogador e o Time de todas as Swallowtails que ele carregava.
 * Enquanto o Tempo estiver abaixo de UNLOCK_TIME: o Tempo s\u00f3 sobe roubando (sem regenera\u00e7\u00e3o passiva e sem sacar
 * da Swallowtail) e Chrono Stasis / Chrono Stasis Grigora ficam bloqueados. Ao chegar l\u00e1, tudo \u00e9 devolvido.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RebirthHandler {
    private static final String TAG_REBORN = "chrono_bubble_reborn";
    private static final String TAG_PENDING = "chrono_bubble_pending";
    private static final String TAG_REMOVED = "chrono_bubble_removed";
    /** Quanto Time a Swallowtail gastou para trazer o jogador de volta (s\u00f3 para a mensagem ao renascer). */
    private static final String TAG_RESERVE = "chrono_bubble_reserve";

    private static final Map<UUID, Integer> LAST_TIME = new HashMap<>();

    /** O jogador est\u00e1 com o grim\u00f3rio de uma p\u00e1gina (renascido e ainda sem os poderes de volta). */
    public static boolean isReborn(PlayerEntity player) {
        return player.getPersistentData().getBoolean(TAG_REBORN);
    }

    /**
     * Um ganho de Time leg\u00edtimo (roubo feito por este addon) acabou de acontecer: aceita o valor atual como base,
     * para o bloqueio da regenera\u00e7\u00e3o passiva n\u00e3o desfazer um roubo pequeno (+1 ou +2).
     */
    public static void acceptTime(PlayerEntity player) {
        if (!isReborn(player)) return;
        int time = BlockCloverBridge.getTime(player);
        if (time >= 0) LAST_TIME.put(player.getUUID(), time);
    }

    // ---------- Morte e respawn ----------

    /** LOWEST e sem receber eventos cancelados: se outro mod impedir a morte, a Swallowtail n\u00e3o \u00e9 esvaziada. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!ChronoConfig.REBIRTH_ENABLED.get() || !(event.getEntityLiving() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getEntityLiving();
        if (!BlockCloverBridge.isTimeUser(player)) return;
        // a Swallowtail gasta TODO o Time guardado agora, antes de o invent\u00e1rio cair no ch\u00e3o
        int used = SwallowtailItem.drainAll(player);
        if (used <= 0) return;                         // nada guardado: sem renascimento
        player.getPersistentData().putBoolean(TAG_PENDING, true);
        player.getPersistentData().putInt(TAG_RESERVE, used);
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
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TIME.remove(event.getPlayer().getUUID());
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
        if (!ChronoConfig.REBIRTH_ENABLED.get() || event.phase != TickEvent.Phase.END || event.side.isClient()) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        CompoundNBT data = player.getPersistentData();

        if (data.getBoolean(TAG_PENDING) && player.isAlive() && player.tickCount > 20) {
            applyRebirth(player);
        }
        if (!data.getBoolean(TAG_REBORN)) return;

        int time = BlockCloverBridge.getTime(player);
        if (time < 0) return;
        if (time >= ChronoConfig.REBIRTH_UNLOCK_TIME.get()) {
            unlock(player);
            return;
        }

        // Bloqueia a regenera\u00e7\u00e3o passiva (+1/s). Compara com o tick anterior em TODO tick: antes s\u00f3 olhava quando
        // tickCount % 20 == 0, e a regenera\u00e7\u00e3o do Block Clover que ca\u00eda em outro tick passava sem ser vista.
        // Roubos do Block Clover (+10) s\u00e3o maiores e continuam valendo; os roubos deste addon chamam acceptTime().
        UUID id = player.getUUID();
        Integer last = LAST_TIME.get(id);
        if (ChronoConfig.REBIRTH_BLOCK_REGEN.get() && last != null) {
            int delta = time - last;
            if (delta >= 1 && delta <= 2) {
                BlockCloverBridge.setTime(player, last);
                BlockCloverBridge.syncToClient(player);
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
        int used = data.getInt(TAG_RESERVE);
        data.putInt(TAG_RESERVE, 0);

        // renasceu: tudo zera (Time do jogador e qualquer Swallowtail que ainda esteja com ele, ex.: keepInventory)
        SwallowtailItem.drainAll(player);
        data.putBoolean(TAG_REBORN, true);
        BlockCloverBridge.setTime(player, 0);
        LAST_TIME.put(player.getUUID(), 0);
        addRemoved(data, BlockCloverBridge.lockStasisAbilities(player));
        BlockCloverBridge.syncToClient(player);
        broadcastState(player);
        Msg.chat(player, TextFormatting.AQUA, "rebirth", used, ChronoConfig.REBIRTH_UNLOCK_TIME.get());
        ChronoAdvancements.grant(player, ChronoAdvancements.REBIRTH);
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
        Msg.bar(player, TextFormatting.AQUA, "rebirth_unlocked");
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
