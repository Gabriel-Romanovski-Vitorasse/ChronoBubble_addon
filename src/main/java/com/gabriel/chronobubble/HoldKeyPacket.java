package com.gabriel.chronobubble;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: a tecla modificadora do Chrono Stasis sustentado (Alt) foi pressionada/solta. */
public class HoldKeyPacket {
    private final boolean down;

    public HoldKeyPacket(boolean down) { this.down = down; }

    public static void encode(HoldKeyPacket p, PacketBuffer buf) { buf.writeBoolean(p.down); }

    public static HoldKeyPacket decode(PacketBuffer buf) { return new HoldKeyPacket(buf.readBoolean()); }

    public static void handle(HoldKeyPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayerEntity sender = ctx.get().getSender();
            if (sender != null) SustainedStasis.setHeld(sender, p.down);
        });
        ctx.get().setPacketHandled(true);
    }
}
