package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "a esfera desta entidade est\u00e1 inst\u00e1vel (mana quase acabando)". */
public class UnstablePacket {
    private final int entityId;
    private final boolean unstable;

    public UnstablePacket(int entityId, boolean unstable) {
        this.entityId = entityId;
        this.unstable = unstable;
    }

    public static void encode(UnstablePacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeBoolean(p.unstable);
    }

    public static UnstablePacket decode(PacketBuffer buf) {
        return new UnstablePacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(UnstablePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientStasis.setUnstable(p.entityId, p.unstable)));
        ctx.get().setPacketHandled(true);
    }
}
