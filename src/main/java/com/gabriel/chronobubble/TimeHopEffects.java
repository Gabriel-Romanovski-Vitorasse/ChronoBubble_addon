package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Efeitos extras do Time Hop ORIGINAL (o teletransporte continua sendo o do Block Clover):
 * sem dano de queda, chegada protegida, pulso de tempo parado na chegada e rastro fantasma na partida.
 * Detecta o salto comparando a posi\u00e7\u00e3o do jogador logo depois do uso, ent\u00e3o s\u00f3 age se o salto realmente aconteceu.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeHopEffects {

    private static final class Pending {
        final Vector3d from; final long tick; int waited;
        final float yRot, xRot, body, head;
        Pending(ServerPlayerEntity p) { this.from = p.position(); this.tick = p.level.getGameTime(); this.yRot = p.yRot; this.xRot = p.xRot; this.body = p.yBodyRot; this.head = p.yHeadRot; }
    }
    private static final class Pulse {
        final ServerWorld world; final Vector3d at; int age; final int total;
        final List<ProjectileEntity> shots = new ArrayList<>();
        final List<Vector3d> motion = new ArrayList<>();
        final List<Boolean> grav = new ArrayList<>();
        Pulse(ServerWorld w, Vector3d at, int total) { this.world = w; this.at = at; this.total = total; }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Long> NO_FALL = new HashMap<>();
    private static final Map<UUID, Long> PROTECT = new HashMap<>();
    private static final List<Pulse> PULSES = new ArrayList<>();

    /** Chamado quando o jogador aciona o Time Hop (antes do teletransporte original rodar). */
    public static void noteUse(ServerPlayerEntity p) {
        if (!ChronoConfig.HOP_ENABLED.get()) return;
        PENDING.put(p.getUUID(), new Pending(p));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity p = (ServerPlayerEntity) event.player;
        Pending pend = PENDING.get(p.getUUID());
        if (pend == null) return;
        pend.waited++;
        if (p.position().distanceTo(pend.from) > 1.5D) {
            PENDING.remove(p.getUUID());
            onHopped(p, pend);
        } else if (pend.waited > 5) {
            PENDING.remove(p.getUUID());               // o salto n\u00e3o aconteceu (recarga, sem mana...)
        }
    }

    private static void onHopped(ServerPlayerEntity p, Pending pend) {
        long now = p.level.getGameTime();
        if (ChronoConfig.HOP_FALL_TICKS.get() > 0) NO_FALL.put(p.getUUID(), now + ChronoConfig.HOP_FALL_TICKS.get());
        if (ChronoConfig.HOP_PROTECT_TICKS.get() > 0) PROTECT.put(p.getUUID(), now + ChronoConfig.HOP_PROTECT_TICKS.get());
        p.fallDistance = 0.0F;
        if (!(p.level instanceof ServerWorld)) return;
        ServerWorld w = (ServerWorld) p.level;
        if (ChronoConfig.HOP_TRAIL.get() && ChronoConfig.HOP_TRAIL_TICKS.get() > 0) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                            new PacketDistributor.TargetPoint(pend.from.x, pend.from.y, pend.from.z, 96.0D, w.dimension())),
                    new HopGhostPacket(p.getId(), pend.from.x, pend.from.y, pend.from.z,
                            pend.yRot, pend.xRot, pend.body, pend.head, ChronoConfig.HOP_TRAIL_TICKS.get()));
        }
        if (ChronoConfig.HOP_PULSE.get() && ChronoConfig.HOP_PULSE_TICKS.get() > 0) startPulse(p, w);
    }

    private static void startPulse(ServerPlayerEntity p, ServerWorld w) {
        Pulse pulse = new Pulse(w, p.position(), ChronoConfig.HOP_PULSE_TICKS.get());
        Effect effect = BlockCloverBridge.stasisEffect();
        AxisAlignedBB box = p.getBoundingBox().inflate(ChronoConfig.f(ChronoConfig.HOP_PULSE_RADIUS));
        if (effect != null) {
            for (LivingEntity e : w.getEntitiesOfClass(LivingEntity.class, box, x -> x != p && x.isAlive())) {
                if (e.distanceTo(p) > ChronoConfig.f(ChronoConfig.HOP_PULSE_RADIUS)) continue;
                if (e instanceof PlayerEntity && !ChronoConfig.HOP_PULSE_PLAYERS.get()) continue;
                if (!ChronoConfig.HOP_PULSE_BOSSES.get() && (e instanceof EnderDragonEntity || e instanceof WitherEntity)) continue;
                if (e.hasEffect(effect)) continue;       // j\u00e1 parado por outro efeito: n\u00e3o mexe
                e.addEffect(new EffectInstance(effect, ChronoConfig.HOP_PULSE_TICKS.get()));
            }
        }
        for (ProjectileEntity s : w.getEntitiesOfClass(ProjectileEntity.class, box, x -> x.isAlive())) {
            if (s.distanceTo(p) > ChronoConfig.f(ChronoConfig.HOP_PULSE_RADIUS)) continue;
            if (s.getOwner() == p) continue;
            pulse.shots.add(s);
            pulse.motion.add(s.getDeltaMovement());
            pulse.grav.add(s.isNoGravity());
            s.setNoGravity(true);
            s.setDeltaMovement(Vector3d.ZERO);
            s.hurtMarked = true;                     // manda a velocidade nova para os clientes
        }
        PULSES.add(pulse);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), ChronoBubbleMod.STASIS_START.get(),
                SoundCategory.PLAYERS, 0.9F, 1.4F);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Iterator<Pulse> it = PULSES.iterator(); it.hasNext(); ) {
            Pulse pu = it.next();
            pu.age++;
            // anel que se expande at\u00e9 o raio do pulso
            double r = ChronoConfig.f(ChronoConfig.HOP_PULSE_RADIUS) * Math.min(1.0D, pu.age / 6.0D);
            if (pu.age <= 8) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2.0D / 24.0D;
                    pu.world.sendParticles(ParticleTypes.END_ROD, pu.at.x + Math.cos(a) * r, pu.at.y + 0.2D,
                            pu.at.z + Math.sin(a) * r, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
            for (int i = 0; i < pu.shots.size(); i++) {
                ProjectileEntity s = pu.shots.get(i);
                if (s.isAlive()) { s.setDeltaMovement(Vector3d.ZERO); s.hurtMarked = true; }
            }
            if (pu.age >= pu.total) {
                releaseShots(pu);
                pu.world.playSound(null, pu.at.x, pu.at.y, pu.at.z, ChronoBubbleMod.STASIS_END.get(),
                        SoundCategory.PLAYERS, 0.8F, 1.2F);
                it.remove();
            }
        }
    }

    /** Devolve aos proj\u00e9teis do pulso a velocidade e a gravidade que tinham antes. */
    private static void releaseShots(Pulse pu) {
        for (int i = 0; i < pu.shots.size(); i++) {
            ProjectileEntity s = pu.shots.get(i);
            if (!s.isAlive()) continue;
            s.setNoGravity(pu.grav.get(i));
            s.setDeltaMovement(pu.motion.get(i));
            s.hurtMarked = true;
        }
    }

    /**
     * O servidor vai fechar: solta os proj\u00e9teis antes de o mundo ser salvo (sen\u00e3o eles ficariam parados no ar
     * para sempre, sem gravidade) e esquece tudo. Sem isso, os pulsos ficavam guardando o mundo antigo e
     * continuavam rodando no pr\u00f3ximo mundo aberto no singleplayer.
     */
    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        for (Pulse pu : PULSES) releaseShots(pu);
        PULSES.clear();
        PENDING.clear();
        NO_FALL.clear();
        PROTECT.clear();
    }

    private static boolean active(Map<UUID, Long> map, Entity e) {
        Long until = map.get(e.getUUID());
        if (until == null) return false;
        if (e.level.getGameTime() >= until) { map.remove(e.getUUID()); return false; }
        return true;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntityLiving() instanceof PlayerEntity) || event.getEntityLiving().level.isClientSide) return;
        if (active(NO_FALL, event.getEntityLiving())) {
            event.setDamageMultiplier(0.0F);
            event.setDistance(0.0F);
            NO_FALL.remove(event.getEntityLiving().getUUID());     // pousou: acabou a prote\u00e7\u00e3o
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().level.isClientSide) return;
        if (event.getSource().isBypassInvul()) return;          // vazio e /kill passam normalmente
        if (event.getEntityLiving() instanceof PlayerEntity && active(PROTECT, event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntityLiving().level.isClientSide) return;
        if (event.getEntityLiving() instanceof PlayerEntity && active(PROTECT, event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClear(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getPlayer().getUUID();
        PENDING.remove(id); NO_FALL.remove(id); PROTECT.remove(id);
    }
}
