package com.gabriel.chronobubble;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: "apertei a tecla de acelerar o tempo". */
public class AccelTogglePacket {
    public static void encode(AccelTogglePacket p, PacketBuffer buf) { }

    public static AccelTogglePacket decode(PacketBuffer buf) { return new AccelTogglePacket(); }

    public static void handle(AccelTogglePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender != null) TimeAcceleration.toggle(sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
