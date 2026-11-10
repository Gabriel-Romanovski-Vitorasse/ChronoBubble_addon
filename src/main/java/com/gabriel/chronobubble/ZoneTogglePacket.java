package com.gabriel.chronobubble;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "apertei a tecla da zona de Chrono Stasis". */
public class ZoneTogglePacket {
    public static void encode(ZoneTogglePacket p, PacketBuffer buf) { }

    public static ZoneTogglePacket decode(PacketBuffer buf) { return new ZoneTogglePacket(); }

    public static void handle(ZoneTogglePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender != null) ChronoZone.toggle(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
