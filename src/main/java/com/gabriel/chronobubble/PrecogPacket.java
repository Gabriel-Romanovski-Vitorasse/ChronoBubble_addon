package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: lista de amea\u00e7as prestes a te atingir (id da entidade e tipo: 0 proj\u00e9til, 1 mob). */
public class PrecogPacket {
    private final int[] ids;
    private final byte[] kinds;

    public PrecogPacket(int[] ids, byte[] kinds) {
        this.ids = ids;
        this.kinds = kinds;
    }

    public static void encode(PrecogPacket p, PacketBuffer buf) {
        buf.writeVarInt(p.ids.length);
        for (int i = 0; i < p.ids.length; i++) {
            buf.writeInt(p.ids[i]);
            buf.writeByte(p.kinds[i]);
        }
    }

    public static PrecogPacket decode(PacketBuffer buf) {
        int n = buf.readVarInt();
        int[] ids = new int[n];
        byte[] kinds = new byte[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readInt();
            kinds[i] = buf.readByte();
        }
        return new PrecogPacket(ids, kinds);
    }

    public static void handle(PrecogPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPrecog.update(p.ids, p.kinds)));
        ctx.get().setPacketHandled(true);
    }
}
