package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "a zona de Chrono Stasis deste jogador ligou/desligou". */
public class ZoneStatePacket {
    private final int entityId;
    private final boolean active;

    public ZoneStatePacket(int entityId, boolean active) {
        this.entityId = entityId;
        this.active = active;
    }

    public static void encode(ZoneStatePacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeBoolean(p.active);
    }

    public static ZoneStatePacket decode(PacketBuffer buf) {
        return new ZoneStatePacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(ZoneStatePacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientZone.set(p.entityId, p.active)));
        ctx.get().setPacketHandled(true);
    }
}
