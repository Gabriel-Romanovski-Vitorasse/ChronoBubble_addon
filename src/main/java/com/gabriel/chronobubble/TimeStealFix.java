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
    /** Diagn\u00f3stico: logs/debug.log, procure por "[ChronoBubble/TimeSteal]". */
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger();


    private static final class Before {
        final int time;
        final float mana;
        Before(int time, float mana) { this.time = time; this.mana = mana; }
    }

    private static final Map<UUID, Before> PENDING = new HashMap<>();

    /** Antes do mod original tratar o golpe: guarda o estado se o soco do Time Steal est\u00e1 armado. */
    /** O addon mexe no soco: ganho fixo e/ou apodrecimento. Com os dois desligados, o soco fica como no mod original. */
    private static boolean active() {
        return ChronoConfig.STEAL_PUNCH_GAIN.get() > 0 || ChronoConfig.STEAL_PUNCH_DELAY.get() > 0.0D;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(LivingHurtEvent event) {
        if (!active() || event.getEntityLiving().level.isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getSource().getEntity();
        if (!BlockCloverBridge.isStealPunchActive(player)) return;
        PENDING.put(player.getUUID(), new Before(BlockCloverBridge.getTime(player), BlockCloverBridge.getMana(player)));
        LOG.debug("[ChronoBubble/TimeSteal] soco armado acertou {} (fonte '{}'): Time {}, mana {}", event.getEntityLiving().getName().getString(),
                event.getSource().getMsgId(), BlockCloverBridge.getTime(player), BlockCloverBridge.getMana(player));
    }

    /**
     * Depois: se o soco realmente acertou (gastou mana), o Time passa a ser o de antes + o ganho (fixo, ou o do mod
     * original). Com apodrecimento ligado, nada acontece na hora: o dano do soco \u00e9 cancelado, o Time volta ao de
     * antes, e os dois chegam aos poucos pelo {@link TimeRot}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void after(LivingHurtEvent event) {
        if (!active() || event.getEntityLiving().level.isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getSource().getEntity();
        Before b = PENDING.remove(player.getUUID());
        if (b == null || b.time < 0) return;
        LOG.debug("[ChronoBubble/TimeSteal] depois do soco: mana {} -> {}, Time {} -> {}, dano {}, cancelado {}", b.mana,
                BlockCloverBridge.getMana(player), b.time, BlockCloverBridge.getTime(player), event.getAmount(), event.isCanceled());
        if (!(BlockCloverBridge.getMana(player) < b.mana - 1.0F)) return;   // o soco n\u00e3o foi consumido
        int fixed = ChronoConfig.STEAL_PUNCH_GAIN.get();
        int gain = fixed > 0 ? fixed : Math.max(0, BlockCloverBridge.getTime(player) - b.time);   // 0 = ganho original
        int delay = (int) Math.round(ChronoConfig.STEAL_PUNCH_DELAY.get() * 20.0D);
        if (delay <= 0) {                                                  // na hora (comportamento anterior)
            if (fixed > 0) {
                BlockCloverBridge.setTime(player, b.time + fixed);
                BlockCloverBridge.syncToClient(player);
                RebirthHandler.acceptTime(player);
            }
            return;
        }
        float damage = event.isCanceled() ? 0.0F : event.getAmount();
        event.setCanceled(true);                                           // o dano vem aos poucos
        BlockCloverBridge.setTime(player, b.time);                         // o Time tamb\u00e9m
        BlockCloverBridge.syncToClient(player);
        TimeRot.start(event.getEntityLiving(), player, damage, gain, delay);
    }
}
