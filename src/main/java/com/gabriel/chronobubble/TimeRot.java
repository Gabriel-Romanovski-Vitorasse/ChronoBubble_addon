package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Apodrecimento do soco do Time Steal: em vez de roubar na hora, o alvo "sente o tempo passar" por alguns segundos.
 * A cada pulso (meio segundo) ele leva uma parte do dano do soco e quem bateu ganha uma parte do Time. Os totais são
 * os mesmos do roubo instantâneo; só ficam distribuídos no tempo. Se o alvo morrer no meio, o resto se perde.
 * Quem começa o apodrecimento é o {@link TimeStealFix}, que detecta o soco.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeRot {
    /** Intervalo entre os pulsos, em ticks. */
    static final int PULSE_TICKS = 10;

    private static final class Rot {
        final LivingEntity target;
        final UUID attacker;
        float damageLeft;
        int timeLeft;
        int pulsesLeft;
        long nextPulse;
        int gained;

        Rot(LivingEntity target, UUID attacker, float damage, int time, int pulses, long firstPulse) {
            this.target = target;
            this.attacker = attacker;
            this.damageLeft = damage;
            this.timeLeft = time;
            this.pulsesLeft = pulses;
            this.nextPulse = firstPulse;
        }
    }

    private static final List<Rot> ROTS = new ArrayList<>();

    /** Quantos pulsos cabem em {@code ticks} (pelo menos 1). */
    static int pulsesFor(int ticks) {
        return Math.max(1, Math.round(ticks / (float) PULSE_TICKS));
    }

    /** Parte inteira de {@code left} para este pulso; o último pulso leva o resto, então a soma é sempre exata. */
    static int share(int left, int pulsesLeft) {
        return pulsesLeft <= 1 ? left : left / pulsesLeft;
    }

    /**
     * Começa o apodrecimento.
     *
     * @param damage dano total do soco (antes da armadura, como no evento)
     * @param time   Time total a ganhar
     * @param ticks  duração
     */
    public static void start(LivingEntity target, ServerPlayerEntity attacker, float damage, int time, int ticks) {
        if (target.level.isClientSide || !target.isAlive()) return;
        long now = target.level.getGameTime();
        ROTS.add(new Rot(target, attacker.getUUID(), Math.max(0.0F, damage), Math.max(0, time), pulsesFor(ticks), now + PULSE_TICKS));
        target.level.playSound(null, target.getX(), target.getY(), target.getZ(), ChronoBubbleMod.STASIS_END.get(),
                SoundCategory.PLAYERS, 0.7F, 0.45F);
        rotParticles(target, 18);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ROTS.isEmpty()) return;
        for (Iterator<Rot> it = ROTS.iterator(); it.hasNext(); ) {
            Rot r = it.next();
            LivingEntity target = r.target;
            ServerPlayerEntity attacker = target.getServer() == null ? null
                    : target.getServer().getPlayerList().getPlayer(r.attacker);
            if (!target.isAlive() || attacker == null) {                        // morreu ou quem bateu saiu: acabou
                finish(r, attacker);
                it.remove();
                continue;
            }
            long now = target.level.getGameTime();
            if (now % 2 == 0) rotParticles(target, 3);                           // apodrecendo entre os pulsos
            if (ChronoConfig.STEAL_ROT_SLOW.get() && now % 10 == 0) {
                target.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 15, 1, false, false, false));
            }
            if (now < r.nextPulse) continue;

            float dmg = r.pulsesLeft <= 1 ? r.damageLeft : r.damageLeft / r.pulsesLeft;
            int time = share(r.timeLeft, r.pulsesLeft);
            r.damageLeft -= dmg;
            r.timeLeft -= time;
            r.pulsesLeft--;
            r.nextPulse += PULSE_TICKS;

            if (dmg > 0.0F) SustainedStasis.hurtDirect(target, rotDamage(attacker), dmg);
            if (time > 0) {
                BlockCloverBridge.spend(attacker, 0.0F, -time, 0);              // tempo negativo = ganha Time
                BlockCloverBridge.syncToClient(attacker);
                RebirthHandler.acceptTime(attacker);
                r.gained += time;
            }
            rotParticles(target, 12);
            target.level.playSound(null, target.getX(), target.getY(), target.getZ(), ChronoBubbleMod.STASIS_END.get(),
                    SoundCategory.PLAYERS, 0.35F, 0.35F + 0.1F * r.pulsesLeft);
            if (r.pulsesLeft <= 0) {
                finish(r, attacker);
                it.remove();
            }
        }
    }

    private static void finish(Rot r, ServerPlayerEntity attacker) {
        if (attacker != null && r.gained > 0) Msg.bar(attacker, TextFormatting.AQUA, "steal_rot_done", r.gained);
    }

    private static void rotParticles(LivingEntity target, int count) {
        if (!(target.level instanceof ServerWorld)) return;
        ServerWorld w = (ServerWorld) target.level;
        double y = target.getY() + target.getBbHeight() * 0.5D;
        double sx = target.getBbWidth() * 0.4D, sy = target.getBbHeight() * 0.35D;
        w.sendParticles(ParticleTypes.ASH, target.getX(), y, target.getZ(), count, sx, sy, sx, 0.01D);
        w.sendParticles(ParticleTypes.SMOKE, target.getX(), y, target.getZ(), Math.max(1, count / 3), sx, sy, sx, 0.005D);
    }

    /** Dano do apodrecimento (a armadura vale, como no soco). */
    private static EntityDamageSource rotDamage(ServerPlayerEntity attacker) {
        return new EntityDamageSource("chrono_rot", attacker);
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ROTS.clear();
    }
}
