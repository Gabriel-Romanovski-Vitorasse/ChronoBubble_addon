package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tempo avançado: quando o Chrono Stasis normal acaba sozinho, o tempo do alvo salta à frente e quem o congelou
 * paga Time pela vida dele (conta em {@link AgingRule}). Se o Time cobre a vida toda, o alvo envelhece até virar pó.
 * <p>
 * Só vale para o Chrono Stasis "normal": o soco do Block Clover e a esfera arremessada comum. Para saber quem
 * congelou:
 * <ul>
 *   <li>esfera: marcada direto por {@link StasisOrbEntity} ({@link #markOrb});</li>
 *   <li>soco: o efeito aparece no alvo, e no tick seguinte procura-se o jogador por perto que armou o soco do
 *   Chrono Stasis há pouco e cujo último alvo atacado é esse mob ({@link #armPunch}).</li>
 * </ul>
 * O sustentado, o Grigora e o Time Hop tiram/trocam o efeito ou não marcam o alvo, então não envelhecem. Se o efeito
 * for removido antes do fim (leite, troca para sustentado...), a marca some e nada acontece.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeAging {
    /** Quanto tempo depois de armar o soco ele ainda conta (o soco do Block Clover fica "armado" até acertar). */
    private static final int PUNCH_WINDOW_TICKS = 200;
    private static final double PUNCH_REACH = 6.0D;

    /** Alvo (uuid) -> quem congelou (uuid). */
    private static final Map<UUID, UUID> MARKED = new HashMap<>();
    /** Jogador -> quando armou o soco do Chrono Stasis. */
    private static final Map<UUID, Long> PUNCH_ARMED = new HashMap<>();

    private static final class Pending {
        final LivingEntity target;
        final long tick;
        Pending(LivingEntity target, long tick) { this.target = target; this.tick = tick; }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    // ---------- marcação ----------

    /** O jogador usou o Chrono Stasis sem Shift/Alt: o soco está armado. */
    public static void armPunch(ServerPlayerEntity player) {
        PUNCH_ARMED.put(player.getUUID(), player.level.getGameTime());
    }

    /** A esfera arremessada comum congelou {@code target}. */
    public static void markOrb(LivingEntity target, PlayerEntity caster) {
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_ORB.get() || caster == null) return;
        MARKED.put(target.getUUID(), caster.getUUID());
    }

    @SubscribeEvent
    public static void onAdded(PotionEvent.PotionAddedEvent event) {
        EffectInstance inst = event.getPotionEffect();
        LivingEntity target = event.getEntityLiving();
        if (inst == null || target.level.isClientSide || !BlockCloverBridge.isStasis(inst.getEffect())) return;
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_PUNCH.get()) return;
        if (event.getOldPotionEffect() != null || MARKED.containsKey(target.getUUID())) return;
        PENDING.add(new Pending(target, target.level.getGameTime()));        // decide no próximo tick (soco ou não)
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            LivingEntity target = p.target;
            long now = target.level.getGameTime();
            if (now <= p.tick) continue;                                        // espera o soco terminar de registrar
            it.remove();
            if (!target.isAlive() || MARKED.containsKey(target.getUUID())) continue;
            PlayerEntity puncher = findPuncher(target, now);
            if (puncher != null) {
                MARKED.put(target.getUUID(), puncher.getUUID());
                PUNCH_ARMED.remove(puncher.getUUID());
            }
        }
    }

    /** Jogador por perto que armou o soco há pouco e cujo último alvo atacado é este. */
    private static PlayerEntity findPuncher(LivingEntity target, long now) {
        for (PlayerEntity p : target.level.players()) {
            Long armed = PUNCH_ARMED.get(p.getUUID());
            if (armed == null || now - armed > PUNCH_WINDOW_TICKS) continue;
            if (p.distanceToSqr(target) > PUNCH_REACH * PUNCH_REACH) continue;
            if (p.getLastHurtMob() != target) continue;
            if (p.tickCount - p.getLastHurtMobTimestamp() > 3) continue;
            return p;
        }
        return null;
    }

    // ---------- fim do congelamento ----------

    @SubscribeEvent
    public static void onExpired(PotionEvent.PotionExpiryEvent event) {
        EffectInstance inst = event.getPotionEffect();
        LivingEntity target = event.getEntityLiving();
        if (inst == null || target.level.isClientSide || !BlockCloverBridge.isStasis(inst.getEffect())) return;
        UUID caster = MARKED.remove(target.getUUID());
        if (caster != null) advance(target, caster);
    }

    /** Efeito tirado antes do fim (leite, sustentado, Time Hop...): sem tempo avançado. */
    @SubscribeEvent
    public static void onRemoved(PotionEvent.PotionRemoveEvent event) {
        if (BlockCloverBridge.isStasis(event.getPotion())) MARKED.remove(event.getEntityLiving().getUUID());
    }

    /** O Wither não recebe efeitos: o {@link WitherStasis} avisa aqui quando o congelamento dele acaba sozinho. */
    public static void onBossFreezeEnded(LivingEntity boss) {
        UUID caster = MARKED.remove(boss.getUUID());
        if (caster != null) advance(boss, caster);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        MARKED.remove(event.getEntityLiving().getUUID());
        PUNCH_ARMED.remove(event.getEntityLiving().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        MARKED.clear();
        PUNCH_ARMED.clear();
        PENDING.clear();
    }

    private static boolean isBoss(Entity e) {
        return e instanceof WitherEntity || e instanceof EnderDragonEntity;
    }

    /** O tempo do alvo salta à frente: quem congelou paga Time pela vida dele. */
    static void advance(LivingEntity target, UUID casterId) {
        if (!ChronoConfig.AGING_ENABLED.get() || !target.isAlive() || !(target.level instanceof ServerWorld)) return;
        if (target instanceof PlayerEntity && !ChronoConfig.AGING_PLAYERS.get()) return;
        ServerWorld world = (ServerWorld) target.level;
        PlayerEntity caster = world.getServer().getPlayerList().getPlayer(casterId);
        if (caster == null || !caster.isAlive() || caster == target) return;   // saiu do jogo: o tempo não avança

        float health = target.getHealth() + target.getAbsorptionAmount();
        AgingRule.Result r = AgingRule.resolve(health, target.getMaxHealth(), BlockCloverBridge.getTime(caster),
                ChronoConfig.f(ChronoConfig.AGING_TIME_PER_HP), isBoss(target), ChronoConfig.f(ChronoConfig.AGING_BOSS_FRACTION));
        if (r.timeCost <= 0) {
            Msg.bar(caster, TextFormatting.GRAY, "aging_no_time", target.getDisplayName());
            return;
        }

        BlockCloverBridge.spend(caster, 0.0F, r.timeCost, 0);
        BlockCloverBridge.syncToClient(caster);
        SustainedStasis.hurtDirect(target, agingDamage(caster), r.kill ? health + 1.0F : r.damage);

        double y = target.getY() + target.getBbHeight() * 0.5D;
        world.sendParticles(r.kill ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, target.getX(), y, target.getZ(),
                r.kill ? 60 : 25, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.02D);
        world.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), y, target.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.05D);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), ChronoBubbleMod.STASIS_END.get(),
                SoundCategory.PLAYERS, 1.0F, r.kill ? 0.5F : 0.7F);
        if (r.kill) Msg.bar(caster, TextFormatting.AQUA, "aging_kill", target.getDisplayName(), r.timeCost);
        else Msg.bar(caster, TextFormatting.AQUA, "aging_damage", target.getDisplayName(), Msg.f1(r.damage), r.timeCost);
    }

    /** Dano do tempo avançado: ignora armadura e proteções mágicas (o corpo simplesmente envelhece). */
    private static EntityDamageSource agingDamage(PlayerEntity caster) {
        return new EntityDamageSource("chrono_aging", caster) {
            {
                bypassArmor();
                bypassMagic();
            }
        };
    }
}
