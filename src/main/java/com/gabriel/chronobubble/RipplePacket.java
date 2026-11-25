package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: um golpe atingiu a bolha desta entidade, vindo da direção (dx, dy, dz) a partir do centro. */
public class RipplePacket {
    final int entityId;
    final float dx, dy, dz;

    public RipplePacket(int entityId, float dx, float dy, float dz) {
        this.entityId = entityId;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public static void encode(RipplePacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dy);
        buf.writeFloat(p.dz);
    }

    public static RipplePacket decode(PacketBuffer buf) {
        return new RipplePacket(buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(RipplePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientStasis.addRipple(p.entityId, p.dx, p.dy, p.dz)));
        ctx.get().setPacketHandled(true);
    }
}
