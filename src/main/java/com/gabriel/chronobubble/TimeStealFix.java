package com.gabriel.chronobubble;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Time Steal normal (o soco): o Block Clover s\u00f3 d\u00e1 +10 de Time se o seu Time estiver ABAIXO de 1000, ent\u00e3o
 * sem limite de Time o soco deixava de render. Aqui o ganho vira um valor fixo (FIXED_GAIN) em todo acerto.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeStealFix {
    /** Time ganho por soco do Time Steal. 0 = deixa o comportamento original do mod. */
    public static int FIXED_GAIN = 20;

    private static final class Before {
        final int time;
        final float mana;
        Before(int time, float mana) { this.time = time; this.mana = mana; }
    }

    private static final Map<UUID, Before> PENDING = new HashMap<>();

    /** Antes do mod original tratar o golpe: guarda o estado se o soco do Time Steal est\u00e1 armado. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(LivingHurtEvent event) {
        if (FIXED_GAIN <= 0 || event.getEntityLiving().level.isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getSource().getEntity();
        if (!BlockCloverBridge.isStealPunchActive(player)) return;
        PENDING.put(player.getUUID(), new Before(BlockCloverBridge.getTime(player), BlockCloverBridge.getMana(player)));
    }

    /** Depois: se o soco realmente acertou (gastou mana), o Time passa a ser o de antes + o ganho fixo. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void after(LivingHurtEvent event) {
        if (FIXED_GAIN <= 0 || event.getEntityLiving().level.isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getSource().getEntity();
        Before b = PENDING.remove(player.getUUID());
        if (b == null || b.time < 0) return;
        if (!(BlockCloverBridge.getMana(player) < b.mana - 1.0F)) return;   // o soco n\u00e3o foi consumido
        BlockCloverBridge.setTime(player, b.time + FIXED_GAIN);
        BlockCloverBridge.syncToClient(player);
    }
}
