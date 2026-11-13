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
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
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
 *   <li>soco: o efeito aparece no alvo, e no tick seguinte procura-se quem acabou de causar dano a ele e tinha
 *   armado o soco do Chrono Stasis há pouco ({@link #armPunch}). O soco do Block Clover não é um ataque comum
 *   (usa uma fonte de dano própria e aplica o efeito no meio do dano), então o "último alvo atacado" do Minecraft
 *   não serve: o addon anota ele mesmo quem feriu cada alvo ({@link #onHurt}).</li>
 * </ul>
 * O sustentado, o Grigora e o Time Hop tiram/trocam o efeito ou não marcam o alvo, então não envelhecem. Se o efeito
 * for removido antes do fim (leite, troca para sustentado...), a marca some e nada acontece.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeAging {
    /** Diagnóstico: cada etapa vai para o latest.log (procure por "[ChronoBubble/Tempo]"). */
    private static final org.apache.logging.log4j.Logger LOG = org.apache.logging.log4j.LogManager.getLogger();

    /** Quanto tempo depois de armar o soco ele ainda conta (o soco do Block Clover fica "armado" até acertar). */
    private static final int PUNCH_WINDOW_TICKS = 600;
    /** O dano do soco e o efeito acontecem no mesmo tick; a decisão é no seguinte. */
    private static final int HIT_WINDOW_TICKS = 2;

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

    /** Último jogador que feriu cada alvo, e quando. */
    private static final class Hit {
        final UUID player;
        final long tick;
        Hit(UUID player, long tick) { this.player = player; this.tick = tick; }
    }

    private static final Map<UUID, Hit> LAST_HIT = new HashMap<>();

    // ---------- marcação ----------

    /** O jogador usou o Chrono Stasis sem Shift/Alt: o soco está armado. */
    public static void armPunch(ServerPlayerEntity player) {
        PUNCH_ARMED.put(player.getUUID(), player.level.getGameTime());
        LOG.info("[ChronoBubble/Tempo] soco do Chrono Stasis armado por {} (tick {})", player.getName().getString(), player.level.getGameTime());
    }

    /** A esfera arremessada comum congelou {@code target}. */
    public static void markOrb(LivingEntity target, PlayerEntity caster) {
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_ORB.get() || caster == null) return;
        MARKED.put(target.getUUID(), caster.getUUID());
        LOG.info("[ChronoBubble/Tempo] esfera marcou {} (por {})", target.getName().getString(), caster.getName().getString());
    }

    /**
     * Anota o ataque ANTES do Block Clover: no soco do Chrono Stasis, o mod aplica o congelamento no tratamento deste
     * evento (e só depois causa o dano), então este é o primeiro momento em que dá para saber quem bateu.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity) || !(event.getTarget() instanceof LivingEntity)) return;
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_PUNCH.get()) return;
        LivingEntity target = (LivingEntity) event.getTarget();
        LAST_HIT.put(target.getUUID(), new Hit(event.getPlayer().getUUID(), target.level.getGameTime()));
        if (PUNCH_ARMED.containsKey(event.getPlayer().getUUID())) {
            LOG.info("[ChronoBubble/Tempo] ataque anotado: {} atacou {} (tick {})", event.getPlayer().getName().getString(),
                    target.getName().getString(), target.level.getGameTime());
        }
    }

    /** Anota quem feriu o alvo (qualquer fonte de dano com um jogador por trás, inclusive a do soco do Block Clover). */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide || !(event.getSource().getEntity() instanceof ServerPlayerEntity)) return;
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_PUNCH.get()) return;
        LAST_HIT.put(target.getUUID(), new Hit(event.getSource().getEntity().getUUID(), target.level.getGameTime()));
        if (PUNCH_ARMED.containsKey(event.getSource().getEntity().getUUID())) {
            LOG.info("[ChronoBubble/Tempo] golpe anotado: {} feriu {} (fonte '{}', tick {})", event.getSource().getEntity().getName().getString(),
                    target.getName().getString(), event.getSource().getMsgId(), target.level.getGameTime());
        }
    }

    @SubscribeEvent
    public static void onAdded(PotionEvent.PotionAddedEvent event) {
        EffectInstance inst = event.getPotionEffect();
        LivingEntity target = event.getEntityLiving();
        if (inst == null || target.level.isClientSide || !BlockCloverBridge.isStasis(inst.getEffect())) return;
        if (!ChronoConfig.AGING_ENABLED.get() || !ChronoConfig.AGING_FROM_PUNCH.get()) return;
        LOG.info("[ChronoBubble/Tempo] Chrono Stasis aplicado em {} ({} ticks, j\u00e1 tinha: {}, j\u00e1 marcado: {})", target.getName().getString(),
                inst.getDuration(), event.getOldPotionEffect() != null, MARKED.containsKey(target.getUUID()));
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
            pruneHits(now);
            if (!target.isAlive() || MARKED.containsKey(target.getUUID())) continue;
            PlayerEntity puncher = findPuncher(target, now);
            Hit hit = LAST_HIT.get(target.getUUID());
            LOG.info("[ChronoBubble/Tempo] decis\u00e3o do soco para {}: \u00faltimo golpe {} (tick {}), soco armado em {}, resultado: {}",
                    target.getName().getString(), hit == null ? "nenhum" : hit.player, hit == null ? -1 : hit.tick,
                    hit == null ? "-" : PUNCH_ARMED.get(hit.player), puncher == null ? "N\u00c3O marcado" : "marcado por " + puncher.getName().getString());
            if (puncher != null) {
                MARKED.put(target.getUUID(), puncher.getUUID());
                PUNCH_ARMED.remove(puncher.getUUID());
            }
        }
    }

    /** Quem acabou de ferir o alvo e tinha armado o soco do Chrono Stasis há pouco (ou null). */
    private static PlayerEntity findPuncher(LivingEntity target, long now) {
        Hit hit = LAST_HIT.get(target.getUUID());
        if (hit == null || now - hit.tick > HIT_WINDOW_TICKS) return null;
        Long armed = PUNCH_ARMED.get(hit.player);
        if (armed == null || now - armed > PUNCH_WINDOW_TICKS) return null;
        for (PlayerEntity p : target.level.players()) if (p.getUUID().equals(hit.player)) return p;
        return null;
    }

    /** Esquece golpes antigos (o mapa não cresce sem limite). */
    private static void pruneHits(long now) {
        if (LAST_HIT.size() < 256) return;
        LAST_HIT.values().removeIf(h -> now - h.tick > 200);
    }

    // ---------- fim do congelamento ----------

    @SubscribeEvent
    public static void onExpired(PotionEvent.PotionExpiryEvent event) {
        EffectInstance inst = event.getPotionEffect();
        LivingEntity target = event.getEntityLiving();
        if (inst == null || target.level.isClientSide || !BlockCloverBridge.isStasis(inst.getEffect())) return;
        UUID caster = MARKED.remove(target.getUUID());
        LOG.info("[ChronoBubble/Tempo] Chrono Stasis acabou sozinho em {} (marcado: {})", target.getName().getString(), caster != null);
        if (caster != null) advance(target, caster);
    }

    /** Efeito tirado antes do fim (leite, sustentado, Time Hop...): sem tempo avançado. */
    @SubscribeEvent
    public static void onRemoved(PotionEvent.PotionRemoveEvent event) {
        if (BlockCloverBridge.isStasis(event.getPotion()) && MARKED.remove(event.getEntityLiving().getUUID()) != null) {
            LOG.info("[ChronoBubble/Tempo] Chrono Stasis tirado antes do fim de {}: sem tempo avan\u00e7ado", event.getEntityLiving().getName().getString());
        }
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
        LAST_HIT.remove(event.getEntityLiving().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        MARKED.clear();
        PUNCH_ARMED.clear();
        PENDING.clear();
        LAST_HIT.clear();
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
        if (caster == null || !caster.isAlive() || caster == target) {        // saiu do jogo: o tempo não avança
            LOG.info("[ChronoBubble/Tempo] tempo avan\u00e7ado cancelado: quem congelou n\u00e3o est\u00e1 dispon\u00edvel");
            return;
        }

        float health = target.getHealth() + target.getAbsorptionAmount();
        AgingRule.Result r = AgingRule.resolve(health, target.getMaxHealth(), BlockCloverBridge.getTime(caster),
                ChronoConfig.f(ChronoConfig.AGING_TIME_PER_HP), isBoss(target), ChronoConfig.f(ChronoConfig.AGING_BOSS_FRACTION));
        LOG.info("[ChronoBubble/Tempo] tempo avan\u00e7ado em {}: vida {}, Time de {} = {}, resultado {}", target.getName().getString(),
                health, caster.getName().getString(), BlockCloverBridge.getTime(caster), r);
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
