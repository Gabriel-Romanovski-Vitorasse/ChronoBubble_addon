package com.gabriel.chronobubble;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: "voc\u00ea acabou de ganhar N de Time" (roubo de tempo). */
public class TimeGainPacket {
    private final int amount;

    public TimeGainPacket(int amount) { this.amount = amount; }

    public static void encode(TimeGainPacket p, PacketBuffer buf) { buf.writeInt(p.amount); }

    public static TimeGainPacket decode(PacketBuffer buf) { return new TimeGainPacket(buf.readInt()); }

    public static void handle(TimeGainPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientTimeGain.add(p.amount)));
        ctx.get().setPacketHandled(true);
    }
}
