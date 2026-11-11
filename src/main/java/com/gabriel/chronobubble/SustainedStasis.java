package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Chrono Stasis sustentado. Duas formas de come\u00e7ar:
 *  - Alt + Shift + Chrono Stasis: a esfera arremessada prende UM alvo (at\u00e9 MAX_TARGETS ao mesmo tempo);
 *  - Alt + Chrono Stasis Grigora: prende todos em volta de uma vez (modo \u00e1rea).
 * Os alvos ficam presos at\u00e9 voc\u00ea desativar a combat bar, ficar sem mana, morrer ou sair do jogo.
 * O dano que eles recebem parados fica guardado e cai todo de uma vez quando s\u00e3o soltos.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SustainedStasis {
    // ===== Ajustes f\u00e1ceis (o .toml tem prioridade) =====
    public static boolean ENABLED = true;
    /** Quantos alvos podem ficar presos ao mesmo tempo com a esfera (o mais antigo \u00e9 solto ao passar disso). */
    public static int MAX_TARGETS = 3;
    /** Mana gasta por segundo PARA CADA alvo mantido parado (esfera). */
    public static float MANA_PER_SECOND = 4.0F;
    /** Quantos segundos de mana restantes disparam o aviso de "tempo inst\u00e1vel". */
    public static float WARN_SECONDS = 3.0F;
    /** Guarda o dano recebido pelos alvos presos e aplica ao solt\u00e1-los. */
    public static boolean ACCUMULATE_DAMAGE = true;
    public static float DAMAGE_MULTIPLIER = 1.0F;

    // --- Grigora sustentado ---
    public static boolean GRIGORA_ENABLED = true;
    public static float GRIGORA_RADIUS = 16.0F;
    public static int GRIGORA_MAX_TARGETS = 10;
    public static float GRIGORA_MANA_PER_SECOND = 1.5F;
    public static int GRIGORA_TIME_COST = 50;
    public static int GRIGORA_COOLDOWN_TICKS = 200;
    public static boolean GRIGORA_SKIP_BOSSES = true;

    /** Jogadores que est\u00e3o segurando a tecla modificadora (Alt por padr\u00e3o; o cliente avisa o servidor). */
    private static final Set<UUID> HELD = new HashSet<>();

    public static void setHeld(PlayerEntity p, boolean down) {
        if (down) HELD.add(p.getUUID()); else HELD.remove(p.getUUID());
    }

    /** Alt (ou a tecla configurada) pressionada = vers\u00e3o sustentada. */
    public static boolean holdsTrigger(PlayerEntity p) {
        return HELD.contains(p.getUUID());
    }

    private static final int FREEZE_TICKS = 1_000_000;   // "para sempre": quem encerra somos n\u00f3s
    private static final String TAG = "chrono_bubble_sustained";
    private static final ResourceLocation EFFECT_ID = new ResourceLocation("block_clover", "chrono_stasis");

    private static final class Session {
        final List<LivingEntity> targets = new ArrayList<>();
        final float cost;      // mana por segundo por alvo
        final int max;         // m\u00e1ximo de alvos
        int ticks;
        boolean unstable;
        Session(float cost, int max) { this.cost = cost; this.max = max; }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Dano guardado por alvo (uuid do alvo) e quem o prendeu. */
    private static final Map<UUID, Float> ACCUM = new HashMap<>();
    private static final Map<UUID, UUID> OWNER = new HashMap<>();
    private static final Map<UUID, Long> GRIGORA_READY = new HashMap<>();
    private static boolean releasing;

    private static Effect effect() {
        return ForgeRegistries.POTIONS.getValue(EFFECT_ID);
    }

    private static void sendUnstable(LivingEntity target, boolean unstable) {
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new UnstablePacket(target.getId(), unstable));
    }

    private static void addTarget(Session s, ServerPlayerEntity caster, LivingEntity target, Effect effect) {
        if (s.targets.contains(target)) return;
        while (s.targets.size() >= s.max) release(s.targets.remove(0));      // solta o mais antigo
        if (target.hasEffect(effect)) target.removeEffect(effect);           // recome\u00e7a limpo
        target.addEffect(new EffectInstance(effect, FREEZE_TICKS));
        target.getPersistentData().putBoolean(TAG, true);
        OWNER.put(target.getUUID(), caster.getUUID());
        ACCUM.remove(target.getUUID());
        s.targets.add(target);
        if (s.unstable) sendUnstable(target, true);
    }

    /** Prende {@code target} com a esfera (soma aos alvos que o jogador j\u00e1 mant\u00e9m). */
    public static void begin(ServerPlayerEntity caster, LivingEntity target) {
        Effect effect = effect();
        if (!ENABLED || effect == null || target == caster) return;
        Session s = SESSIONS.computeIfAbsent(caster.getUUID(), k -> new Session(MANA_PER_SECOND, MAX_TARGETS));
        addTarget(s, caster, target, effect);
        Msg.bar(caster, TextFormatting.AQUA, "sustain_on", s.targets.size(), s.max, Msg.f1(s.cost * s.targets.size()));
    }

    /** Alt + Grigora: prende todos em volta. Chamado no lugar do Grigora original (que \u00e9 cancelado). */
    public static void beginArea(ServerPlayerEntity caster) {
        Effect effect = effect();
        if (!GRIGORA_ENABLED || effect == null) return;
        long now = caster.level.getGameTime();
        Long ready = GRIGORA_READY.get(caster.getUUID());
        if (ready != null && now < ready) {
            Msg.bar(caster, TextFormatting.GRAY, "grigora_cooldown", Msg.f1((ready - now) / 20.0));
            return;
        }
        if (!(BlockCloverBridge.getTime(caster) > GRIGORA_TIME_COST)) {
            Msg.bar(caster, TextFormatting.RED, "no_time");
            return;
        }
        final double r = GRIGORA_RADIUS;
        List<LivingEntity> found = caster.level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(r),
                e -> e != caster && e.isAlive() && e.distanceToSqr(caster) <= r * r
                        && !(GRIGORA_SKIP_BOSSES && (e instanceof EnderDragonEntity || e instanceof WitherEntity)));
        if (found.isEmpty()) {
            Msg.bar(caster, TextFormatting.GRAY, "nobody_near");
            return;
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        if (found.size() > GRIGORA_MAX_TARGETS) found = new ArrayList<>(found.subList(0, GRIGORA_MAX_TARGETS));
        if (!(BlockCloverBridge.getMana(caster) >= GRIGORA_MANA_PER_SECOND * found.size())) {
            Msg.bar(caster, TextFormatting.RED, "no_mana");
            return;
        }

        end(caster.getUUID(), null, null);
        BlockCloverBridge.spend(caster, 0.0F, GRIGORA_TIME_COST, 0);
        BlockCloverBridge.syncToClient(caster);
        GRIGORA_READY.put(caster.getUUID(), now + GRIGORA_COOLDOWN_TICKS);

        Session s = new Session(GRIGORA_MANA_PER_SECOND, GRIGORA_MAX_TARGETS);
        SESSIONS.put(caster.getUUID(), s);
        for (LivingEntity t : found) addTarget(s, caster, t, effect);
        caster.level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 1.2F, 0.7F);
        Msg.bar(caster, TextFormatting.AQUA, "grigora_on", s.targets.size(), Msg.f1(s.cost * s.targets.size()));
    }

    /** Solta todos os alvos. {@code key} (pode ser null) \u00e9 a mensagem traduz\u00edvel da action bar do jogador. */
    public static void end(UUID casterId, TextFormatting color, String key) {
        Session s = SESSIONS.remove(casterId);
        if (s == null) return;
        PlayerEntity owner = null;
        for (LivingEntity t : s.targets) {
            if (owner == null) owner = t.level.getPlayerByUUID(casterId);
            release(t);
        }
        if (key != null && owner != null) Msg.bar(owner, color, key);
    }

    /** Solta um alvo e aplica de uma vez o dano que ele recebeu enquanto estava parado. */
    private static void release(LivingEntity target) {
        Effect effect = effect();
        target.getPersistentData().remove(TAG);
        Float stored = ACCUM.remove(target.getUUID());
        UUID ownerId = OWNER.remove(target.getUUID());
        if (target.level.isClientSide) return;
        sendUnstable(target, false);
        if (effect != null && target.hasEffect(effect)) target.removeEffect(effect);

        if (stored != null && stored > 0.0F && target.isAlive()) {
            PlayerEntity owner = ownerId != null ? target.level.getPlayerByUUID(ownerId) : null;
            if (target.level instanceof ServerWorld) {
                ((ServerWorld) target.level).sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 30, 0.4D, 0.5D, 0.4D, 0.4D);
            }
            target.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.6F);
            releasing = true;
            try {
                target.invulnerableTime = 0;
                target.hurt(owner != null ? DamageSource.playerAttack(owner) : DamageSource.MAGIC, stored);
            } finally {
                releasing = false;
            }
        }
    }

    /** Causa dano que N\u00c3O fica guardado (ex.: o Time Steal arremess\u00e1vel, que precisa do dano na hora). */
    public static boolean hurtDirect(LivingEntity target, DamageSource source, float amount) {
        boolean prev = releasing;
        releasing = true;
        try {
            target.invulnerableTime = 0;
            return target.hurt(source, amount);
        } finally {
            releasing = prev;
        }
    }

    /** O alvo preso n\u00e3o sofre na hora: o dano fica guardado. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!ACCUMULATE_DAMAGE || releasing) return;
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide || !OWNER.containsKey(target.getUUID())) return;
        DamageSource src = event.getSource();
        if (src.isBypassInvul()) return;                              // vazio / /kill passam normalmente
        float amount = event.getAmount() * DAMAGE_MULTIPLIER;
        if (amount <= 0.0F) return;
        event.setCanceled(true);
        float total = ACCUM.merge(target.getUUID(), amount, Float::sum);
        if (target.level instanceof ServerWorld) {
            ((ServerWorld) target.level).sendParticles(ParticleTypes.ENCHANTED_HIT,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.1D);
        }
        if (src.getEntity() instanceof ServerPlayerEntity) {
            ((ServerPlayerEntity) src.getEntity()).displayClientMessage(
                    Msg.tr(TextFormatting.AQUA, "stored_damage", Msg.f1(total)), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        Session s = SESSIONS.get(event.player.getUUID());
        if (s == null) return;
        PlayerEntity player = event.player;

        if (!player.isAlive()) { end(player.getUUID(), null, null); return; }
        if (!BlockCloverBridge.isInCombat(player)) { end(player.getUUID(), TextFormatting.GRAY, "sustain_combat_off"); return; }

        // alvos que morreram ou sa\u00edram do mundo s\u00e3o soltos um a um
        Iterator<LivingEntity> it = s.targets.iterator();
        while (it.hasNext()) {
            LivingEntity t = it.next();
            if (!t.isAlive() || t.level != player.level) { release(t); it.remove(); }
        }
        if (s.targets.isEmpty()) { end(player.getUUID(), TextFormatting.GRAY, "sustain_no_targets"); return; }

        float rate = s.cost * s.targets.size();
        s.ticks++;
        if (s.ticks % 20 == 0) {
            if (BlockCloverBridge.getMana(player) < rate) {
                end(player.getUUID(), TextFormatting.RED, "sustain_no_mana");
                return;
            }
            BlockCloverBridge.spend(player, rate, 0, 0);
            if (player instanceof ServerPlayerEntity) BlockCloverBridge.syncToClient(player);
        }

        if (s.ticks % 5 == 0) {
            float seconds = BlockCloverBridge.getMana(player) / Math.max(0.01F, rate);
            boolean unstable = seconds <= WARN_SECONDS;
            if (unstable != s.unstable) {
                s.unstable = unstable;
                for (LivingEntity t : s.targets) sendUnstable(t, unstable);
            }
            if (unstable && s.ticks % 20 == 0) {
                Msg.bar(player, TextFormatting.RED, "sustain_unstable", Math.round(Math.max(0.0F, seconds)));
                player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8F, 0.6F);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity) end(event.getEntityLiving().getUUID(), null, null);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getPlayer().getUUID());
        end(event.getPlayer().getUUID(), null, null);
        ChronoThrowHandler.forgetExpired(GRIGORA_READY, event.getPlayer().getUUID(), event.getPlayer().level.getGameTime());
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) end(id, null, null);
        ACCUM.clear();
        OWNER.clear();
    }

    /** Seguran\u00e7a: um mob marcado que volta ao mundo sem sess\u00e3o (jogo fechado/chunk descarregado) \u00e9 solto. */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity e = (LivingEntity) event.getEntity();
        if (!e.getPersistentData().getBoolean(TAG)) return;
        for (Session s : SESSIONS.values()) if (s.targets.contains(e)) return;
        e.getPersistentData().remove(TAG);
        ACCUM.remove(e.getUUID());
        OWNER.remove(e.getUUID());
        Effect effect = effect();
        if (effect != null && e.hasEffect(effect)) e.removeEffect(effect);
    }
}
