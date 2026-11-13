package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Soco do Time Steal (o normal, sem Shift): ganho fixo de Time e apodrecimento.
 * <p>
 * Como o Block Clover 0.5 trata o soco (conferido no bytecode, {@code AbilitiesEvents.onAttackedByPlayer}):
 * <ol>
 *   <li>o jogador ataca: {@link AttackEntityEvent};</li>
 *   <li>no tratamento desse evento o mod já executa o efeito do soco (gasta 15 de mana, dá +10 de Time se o Time
 *   estiver abaixo de 1000) e encerra o soco;</li>
 *   <li>depois cancela o ataque comum e causa o dano com a fonte de dano própria ({@code "ability"}).</li>
 * </ol>
 * Por isso o addon olha o soco no {@link AttackEntityEvent} com prioridade máxima, ANTES do Block Clover (quando o
 * soco ainda está armado), e aplica o resultado no dano que vem logo em seguida (onde dá para cancelar o dano para
 * o apodrecimento). Se esse dano não vier (alvo invulnerável), o resultado é aplicado no fim do evento de ataque.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeStealFix {
    /** Diagnóstico: latest.log, procure por "[ChronoBubble/TimeSteal]". */
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger();

    private static final class Before {
        final int time;
        final float mana;
        final LivingEntity target;
        Before(int time, float mana, LivingEntity target) { this.time = time; this.mana = mana; this.target = target; }
    }

    private static final Map<UUID, Before> PENDING = new HashMap<>();

    /** O addon mexe no soco: ganho fixo e/ou apodrecimento. Com os dois desligados, o soco fica como no mod original. */
    private static boolean active() {
        return ChronoConfig.STEAL_PUNCH_GAIN.get() > 0 || ChronoConfig.STEAL_PUNCH_DELAY.get() > 0.0D;
    }

    /** Antes do Block Clover tratar o ataque: guarda Time e mana se o soco do Time Steal está armado. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeAttack(AttackEntityEvent event) {
        if (!active() || !(event.getPlayer() instanceof ServerPlayerEntity) || !(event.getTarget() instanceof LivingEntity)) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (!BlockCloverBridge.isStealPunchActive(player)) return;
        PENDING.put(player.getUUID(), new Before(BlockCloverBridge.getTime(player), BlockCloverBridge.getMana(player),
                (LivingEntity) event.getTarget()));
        LOG.info("[ChronoBubble/TimeSteal] soco armado atacou {}: Time {}, mana {}", event.getTarget().getName().getString(),
                BlockCloverBridge.getTime(player), BlockCloverBridge.getMana(player));
    }

    /** O dano do soco (logo depois do efeito do Block Clover): aplica o resultado e, com apodrecimento, cancela o dano. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onPunchDamage(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayerEntity) || event.getEntityLiving().level.isClientSide) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.getSource().getEntity();
        Before b = PENDING.get(player.getUUID());
        if (b == null || b.target != event.getEntityLiving()) return;
        PENDING.remove(player.getUUID());
        float damage = event.isCanceled() ? 0.0F : event.getAmount();
        if (apply(player, b, damage)) event.setCanceled(true);                // o dano vem aos poucos
    }

    /** Fim do ataque: se o dano não veio (alvo invulnerável, outro mod cancelou...), aplica o resultado sem dano. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void afterAttack(AttackEntityEvent event) {
        Before b = PENDING.remove(event.getPlayer().getUUID());
        if (b == null || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        apply((ServerPlayerEntity) event.getPlayer(), b, 0.0F);
    }

    /**
     * Se o soco foi mesmo consumido (a mana caiu), o Time passa a ser o de antes + o ganho (fixo, ou o do mod original).
     * Com apodrecimento, nada vem na hora: o Time volta ao de antes e dano e Time chegam aos poucos pelo {@link TimeRot}.
     *
     * @return true se o dano imediato deve ser cancelado (apodrecimento começou)
     */
    private static boolean apply(ServerPlayerEntity player, Before b, float damage) {
        float manaNow = BlockCloverBridge.getMana(player);
        int timeNow = BlockCloverBridge.getTime(player);
        LOG.info("[ChronoBubble/TimeSteal] depois do soco em {}: mana {} -> {}, Time {} -> {}, dano {}",
                b.target.getName().getString(), b.mana, manaNow, b.time, timeNow, damage);
        if (b.time < 0 || !(manaNow < b.mana - 1.0F)) return false;            // o soco não foi consumido
        int fixed = ChronoConfig.STEAL_PUNCH_GAIN.get();
        int gain = fixed > 0 ? fixed : Math.max(0, timeNow - b.time);           // 0 = ganho original do mod
        int delay = (int) Math.round(ChronoConfig.STEAL_PUNCH_DELAY.get() * 20.0D);
        if (delay <= 0) {                                                       // na hora (sem apodrecimento)
            if (fixed > 0) {
                BlockCloverBridge.setTime(player, b.time + fixed);
                BlockCloverBridge.syncToClient(player);
                RebirthHandler.acceptTime(player);
            }
            return false;
        }
        BlockCloverBridge.setTime(player, b.time);                              // o Time também vem aos poucos
        BlockCloverBridge.syncToClient(player);
        TimeRot.start(b.target, player, damage, gain, delay);
        LOG.info("[ChronoBubble/TimeSteal] apodrecimento começou: {} de dano e {} de Time em {} ticks", damage, gain, delay);
        return true;
    }
}
