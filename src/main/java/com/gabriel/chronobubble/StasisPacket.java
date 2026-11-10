package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "a entidade X est\u00e1 em Chrono Stasis por N ticks" (N = 0 cancela). */
public class StasisPacket {
    private final int entityId;
    private final int duration;

    public StasisPacket(int entityId, int duration) {
        this.entityId = entityId;
        this.duration = duration;
    }

    public static void encode(StasisPacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeInt(p.duration);
    }

    public static StasisPacket decode(PacketBuffer buf) {
        return new StasisPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(StasisPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStasis.set(p.entityId, p.duration)));
        ctx.get().setPacketHandled(true);
    }
}
