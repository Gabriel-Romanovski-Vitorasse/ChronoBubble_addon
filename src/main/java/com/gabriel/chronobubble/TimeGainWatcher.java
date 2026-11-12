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
    /**
     * Ganho m\u00ednimo para avisar o cliente. A regenera\u00e7\u00e3o passiva (+1/s) n\u00e3o passa daqui; o que mostrar de fato
     * (ligado/desligado e o m\u00ednimo) \u00e9 op\u00e7\u00e3o visual de cada cliente.
     */
    private static final int SEND_MIN_GAIN = 2;

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
        int max = ChronoConfig.MAX_TIME.get();
        if (max > 0 && now > max) {                                  // acima do limite: volta para o m\u00e1ximo
            BlockCloverBridge.setTime(player, max);
            BlockCloverBridge.syncToClient(player);
            now = max;
        }
        Integer before = LAST.put(player.getUUID(), now);
        if (before == null) return;
        int gain = now - before;
        if (gain >= SEND_MIN_GAIN) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new TimeGainPacket(gain));
        }
    }
}
