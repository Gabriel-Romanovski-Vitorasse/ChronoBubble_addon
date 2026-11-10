package com.gabriel.chronobubble;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Servidor: percebe quando o Time de um jogador sobe de uma vez (o Time Steal d\u00e1 +10;
 * a regenera\u00e7\u00e3o passiva d\u00e1 s\u00f3 +1) e avisa o cliente para mostrar os n\u00fameros romanos subindo.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeGainWatcher {
    // ===== Ajustes f\u00e1ceis =====
    public static boolean ENABLED = true;
    /** Ganho m\u00ednimo, em um tick, para contar como "tempo roubado". */
    public static int MIN_GAIN = 5;
    /** Limite m\u00e1ximo de Time; 0 = sem limite (padr\u00e3o). */
    public static int MAX_TIME = 0;   // 0 = sem limite

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getPlayer().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;
        if (!BlockCloverBridge.isTimeUser(player)) { LAST.remove(player.getUUID()); return; }

        int now = BlockCloverBridge.getTime(player);
        if (now < 0) return;
        if (MAX_TIME > 0 && now > MAX_TIME) {                          // acima do limite: volta para o m\u00e1ximo
            BlockCloverBridge.setTime(player, MAX_TIME);
            BlockCloverBridge.syncToClient(player);
            now = MAX_TIME;
        }
        Integer before = LAST.put(player.getUUID(), now);
        if (before == null) return;
        int gain = now - before;
        if (ENABLED && gain >= MIN_GAIN) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimeGainPacket(gain));
        }
    }
}
