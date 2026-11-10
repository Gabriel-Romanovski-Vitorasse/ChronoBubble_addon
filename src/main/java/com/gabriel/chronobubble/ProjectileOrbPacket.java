package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "este proj\u00e9til foi parado (ou solto) pela zona": desenha/remove o orbe do Chrono Stasis nele. */
public class ProjectileOrbPacket {
    private final int entityId;
    private final boolean on;

    public ProjectileOrbPacket(int entityId, boolean on) {
        this.entityId = entityId;
        this.on = on;
    }

    public static void encode(ProjectileOrbPacket p, PacketBuffer buf) {
        buf.writeInt(p.entityId);
        buf.writeBoolean(p.on);
    }

    public static ProjectileOrbPacket decode(PacketBuffer buf) {
        return new ProjectileOrbPacket(buf.readInt(), buf.readBoolean());
    }

    public static void handle(ProjectileOrbPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientZone.setProjectileOrb(p.entityId, p.on)));
        ctx.get().setPacketHandled(true);
    }
}
