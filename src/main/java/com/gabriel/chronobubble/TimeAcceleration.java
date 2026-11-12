package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;

/**
 * Acelerar o tempo (tecla V): o jogador fica mais rápido e ataca mais rápido (Velocidade + Pressa) e os mobs
 * em volta ficam lentos. Custa mana por segundo e dura no máximo o tempo configurado; depois há uma recarga.
 * O liga/desliga e a cobrança de mana vêm de {@link ManaDrainAbility}.
 */
public class TimeAcceleration extends ManaDrainAbility {
    public static final TimeAcceleration INSTANCE = new TimeAcceleration();

    private TimeAcceleration() {
        super("accel");
    }

    // ---------- valores ----------

    @Override
    protected boolean enabled() {
        return ChronoConfig.ACCEL_ENABLED.get();
    }

    @Override
    protected float manaPerSecond() {
        return ChronoConfig.f(ChronoConfig.ACCEL_MANA_PER_SECOND);
    }

    @Override
    protected float manaToStart() {
        return ChronoConfig.f(ChronoConfig.ACCEL_MANA_TO_START);
    }

    @Override
    protected String missingPowerKey() {
        return "no_power";
    }

    @Override
    protected int cooldownTicks() {
        return ChronoConfig.ACCEL_COOLDOWN_TICKS.get();
    }

    // ---------- comportamento próprio ----------

    @Override
    protected void onStarted(ServerPlayerEntity player) {
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 0.8F, 1.6F);
    }

    @Override
    protected void onStopped(PlayerEntity player, boolean quiet) {
        if (quiet) return;
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_END.get(), SoundCategory.PLAYERS, 0.8F, 1.6F);
    }

    /** Limite de duração e contagem regressiva na action bar. */
    @Override
    protected void afterCharge(PlayerEntity player, int ticks) {
        int max = ChronoConfig.ACCEL_MAX_SECONDS.get();
        if (max > 0 && ticks / 20 >= max) {
            stop(player, TextFormatting.GRAY, "accel_limit");
            return;
        }
        if (max > 0) Msg.bar(player, TextFormatting.AQUA, "accel_left", Math.max(0, max - ticks / 20));
        else Msg.bar(player, TextFormatting.AQUA, "accel_active");
    }

    @Override
    protected void activeTick(PlayerEntity player, int ticks) {
        if (ticks % 10 == 0) {
            // efeitos curtos, renovados a cada 10 ticks (somem sozinhos logo após o fim)
            player.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 25, ChronoConfig.ACCEL_SPEED_LEVEL.get(), false, false, false));
            player.addEffect(new EffectInstance(Effects.DIG_SPEED, 25, ChronoConfig.ACCEL_HASTE_LEVEL.get(), false, false, false));
            if (ChronoConfig.ACCEL_SLOW_ENEMIES.get()) slowAround(player);
        }
        if (ticks % 4 == 0 && player.level instanceof ServerWorld) {
            ((ServerWorld) player.level).sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 0.9D, player.getZ(), 3, 0.35D, 0.6D, 0.35D, 0.4D);
        }
    }

    private static void slowAround(PlayerEntity player) {
        final double r = ChronoConfig.ACCEL_SLOW_RADIUS.get();
        boolean slowPlayers = ChronoConfig.ACCEL_SLOW_PLAYERS.get();
        int level = ChronoConfig.ACCEL_SLOW_LEVEL.get();
        for (LivingEntity e : player.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                x -> x != player && x.isAlive() && x.distanceToSqr(player) <= r * r)) {
            if (e instanceof PlayerEntity && !slowPlayers) continue;
            if (e instanceof TameableEntity && player.getUUID().equals(((TameableEntity) e).getOwnerUUID())) continue;
            e.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 25, level, false, false, false));
        }
    }

    // ---------- acesso pelas outras classes ----------

    public static boolean isActive(PlayerEntity p) {
        return INSTANCE.isRunning(p);
    }

    public static void toggle(ServerPlayerEntity player) {
        INSTANCE.toggleFor(player);
    }
}
