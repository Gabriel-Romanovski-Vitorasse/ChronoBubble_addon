package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "deixe uma bolha de Chrono Stasis com a skin do jogador no ponto de partida do Time Hop". */
public class HopGhostPacket {
    final int playerId;
    final double x, y, z;
    final float yRot, xRot, bodyRot, headRot;
    final int ticks;

    public HopGhostPacket(int playerId, double x, double y, double z,
                          float yRot, float xRot, float bodyRot, float headRot, int ticks) {
        this.playerId = playerId; this.x = x; this.y = y; this.z = z;
        this.yRot = yRot; this.xRot = xRot; this.bodyRot = bodyRot; this.headRot = headRot; this.ticks = ticks;
    }

    public static void encode(HopGhostPacket p, PacketBuffer b) {
        b.writeInt(p.playerId);
        b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
        b.writeFloat(p.yRot); b.writeFloat(p.xRot); b.writeFloat(p.bodyRot); b.writeFloat(p.headRot);
        b.writeInt(p.ticks);
    }

    public static HopGhostPacket decode(PacketBuffer b) {
        return new HopGhostPacket(b.readInt(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt());
    }

    public static void handle(HopGhostPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHopGhost.add(p)));
        ctx.get().setPacketHandled(true);
    }
}
