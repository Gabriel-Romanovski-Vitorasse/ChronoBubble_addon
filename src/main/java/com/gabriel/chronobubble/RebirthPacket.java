package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "este jogador est\u00e1 (ou deixou de estar) com o grim\u00f3rio de uma p\u00e1gina". */
public class RebirthPacket {
    private final int entityId;
    private final boolean reborn;

    public RebirthPacket(int entityId, boolean reborn) {
        this.entityId = entityId;
        this.reborn = reborn;
    }

    public static void encode(RebirthPacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeBoolean(p.reborn);
    }

    public static RebirthPacket decode(PacketBuffer buf) {
        return new RebirthPacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(RebirthPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRebirth.set(p.entityId, p.reborn)));
        ctx.get().setPacketHandled(true);
    }
}
