package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: quantas p\u00e1ginas o grim\u00f3rio deste jogador tem enquanto ele est\u00e1 renascido
 * (1, 4, 16 ou 64), ou {@link GrimoireStages#FULL} quando o grim\u00f3rio est\u00e1 completo.
 */
public class RebirthPacket {
    private final int entityId;
    private final int pages;

    public RebirthPacket(int entityId, int pages) {
        this.entityId = entityId;
        this.pages = pages;
    }

    public static void encode(RebirthPacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeVarInt(p.pages);
    }

    public static RebirthPacket decode(PacketBuffer buf) {
        return new RebirthPacket(buf.readInt(), buf.readVarInt());
    }

    public static void handle(RebirthPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRebirth.set(p.entityId, p.pages)));
        ctx.get().setPacketHandled(true);
    }
}
