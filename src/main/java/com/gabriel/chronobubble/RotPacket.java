package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "esta entidade está apodrecendo pelo Time Steal por N ticks" (tom apodrecido no modelo). */
public class RotPacket {
    private final int entityId;
    private final int ticks;

    public RotPacket(int entityId, int ticks) {
        this.entityId = entityId;
        this.ticks = ticks;
    }

    public static void encode(RotPacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeInt(p.ticks);
    }

    public static RotPacket decode(PacketBuffer buf) {
        return new RotPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(RotPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRot.set(p.entityId, p.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
