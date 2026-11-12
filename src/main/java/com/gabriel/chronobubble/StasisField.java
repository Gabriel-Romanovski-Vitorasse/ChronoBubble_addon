package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Servidor: proj\u00e9teis (flechas, bolas de fogo, magias) que entram numa ZONA de Chrono Stasis ligada
 * (tecla B, ver ChronoZone) ficam parados no ar e retomam a velocidade quando saem dela ou ela some.
 * As esferas em volta dos mobs congelados j\u00e1 N\u00c3O seguram proj\u00e9teis.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StasisField {
    // ===== Ajustes f\u00e1ceis =====
    /** Liga/desliga a parada de proj\u00e9teis. */
    public static final boolean FREEZE_PROJECTILES = true;


    private static final String TAG_HELD = "chrono_bubble_held";

    /** Entidades congeladas (servidor), por id. */
    public static final Map<Integer, LivingEntity> FROZEN = new HashMap<>();
    /** Proj\u00e9teis segurados no momento, por id. */
    private static final Map<Integer, ProjectileEntity> HELD = new HashMap<>();
    /** Quando cada proj\u00e9til foi parado (tempo do mundo), para desfaz\u00ea-lo depois (tempo acelerado). */
    private static final Map<Integer, Long> HELD_AT = new HashMap<>();

    public static float radius(Entity e) {
        return Math.max(e.getBbHeight() * 0.62F, e.getBbWidth() * 0.9F) * ChronoConfig.f(ChronoConfig.SPHERE_SCALE);
    }

    /** Dentro de alguma zona ligada (ChronoZone)? Proj\u00e9teis do pr\u00f3prio dono da zona n\u00e3o contam. */
    private static boolean insideAnyZone(ProjectileEntity p, World world) {
        return zoneHolding(p, world) != null;
    }

    /** Dono da zona que est\u00e1 segurando o proj\u00e9til (o mais pr\u00f3ximo), ou null. */
    private static PlayerEntity zoneHolding(ProjectileEntity p, World world) {
        double r = ChronoConfig.f(ChronoConfig.ZONE_RADIUS) + 0.1D;
        PlayerEntity best = null;
        double bestD = r * r;
        for (PlayerEntity o : ChronoZone.owners(world)) {
            if (ChronoConfig.ZONE_OWN_PASS.get() && p.getOwner() == o) continue;
            double d = p.distanceToSqr(o.getX(), o.getY() + o.getBbHeight() * 0.5D, o.getZ());
            if (d <= bestD) { bestD = d; best = o; }
        }
        return best;
    }

    /**
     * Este proj\u00e9til se desfaz quando a zona acelera o tempo dele? Tridentes nunca (s\u00e3o itens de jogador); flechas
     * comuns conforme a config. Usado tamb\u00e9m pelo cliente, para a anima\u00e7\u00e3o de acelera\u00e7\u00e3o.
     */
    public static boolean dissolves(Entity p) {
        if (ChronoConfig.ZONE_DISSOLVE_SECONDS.get() <= 0.0D) return false;
        if (p instanceof TridentEntity) return false;
        return !(p instanceof AbstractArrowEntity) || ChronoConfig.ZONE_DISSOLVE_ARROWS.get();
    }

    /** Ticks at\u00e9 o proj\u00e9til parado se desfazer. */
    public static int dissolveTicks() {
        return (int) Math.round(ChronoConfig.ZONE_DISSOLVE_SECONDS.get() * 20.0D);
    }

    /** Tempo acelerado at\u00e9 o fim: o proj\u00e9til vira part\u00edculas e some (sem dano e sem item). */
    private static void dissolve(ProjectileEntity p) {
        if (p.level instanceof ServerWorld) {
            ServerWorld w = (ServerWorld) p.level;
            double y = p.getY() + p.getBbHeight() * 0.5D;
            w.sendParticles(ParticleTypes.END_ROD, p.getX(), y, p.getZ(), 10, 0.15D, 0.15D, 0.15D, 0.06D);
            w.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), y, p.getZ(), 14, 0.2D, 0.2D, 0.2D, 0.04D);
            w.sendParticles(ParticleTypes.CLOUD, p.getX(), y, p.getZ(), 4, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        p.level.playSound(null, p.getX(), p.getY(), p.getZ(), ChronoBubbleMod.TIME_DISSOLVE.get(), SoundCategory.PLAYERS, 0.8F, 1.0F);
        p.remove();
    }

    /**
     * Prev\u00ea o pr\u00f3ximo passo do proj\u00e9til (posi\u00e7\u00e3o -> posi\u00e7\u00e3o + velocidade). Se esse trajeto cruza a
     * esfera, devolve a fra\u00e7\u00e3o [0,1] do trajeto em que ele toca a superf\u00edcie (0 = j\u00e1 est\u00e1 dentro).
     * Devolve -1 se n\u00e3o cruza.
     */
    static double crossing(Vector3d pos, Vector3d motion, double cx, double cy, double cz, double r) {
        double ox = pos.x - cx, oy = pos.y - cy, oz = pos.z - cz;
        double c = ox * ox + oy * oy + oz * oz - r * r;
        if (c <= 0.0D) return 0.0D;                                   // j\u00e1 dentro
        double a = motion.lengthSqr();
        if (a < 1.0E-6D) return -1.0D;
        double b = 2.0D * (ox * motion.x + oy * motion.y + oz * motion.z);
        double disc = b * b - 4.0D * a * c;
        if (disc < 0.0D) return -1.0D;
        double t = (-b - Math.sqrt(disc)) / (2.0D * a);
        return (t >= 0.0D && t <= 1.0D) ? t : -1.0D;
    }

    private static void hold(ProjectileEntity p) {
        Vector3d m = p.getDeltaMovement();
        if (m.lengthSqr() < 1.0E-4D) return;       // j\u00e1 est\u00e1 parado (ex.: flecha cravada)
        CompoundNBT tag = p.getPersistentData();
        tag.putBoolean(TAG_HELD, true);
        tag.putDouble("chrono_bubble_mx", m.x);
        tag.putDouble("chrono_bubble_my", m.y);
        tag.putDouble("chrono_bubble_mz", m.z);
        tag.putBoolean("chrono_bubble_ng", p.isNoGravity());
        p.setNoGravity(true);
        p.setDeltaMovement(Vector3d.ZERO);
        p.hurtMarked = true;
        HELD.put(p.getId(), p);
        HELD_AT.put(p.getId(), p.level.getGameTime());
        sendOrb(p, true);
    }

    /** Avisa os clientes que veem o proj\u00e9til para desenhar (ou remover) o orbe do Chrono Stasis nele. */
    private static void sendOrb(ProjectileEntity p, boolean on) {
        if (!ChronoConfig.ZONE_ORBS.get() || p.level.isClientSide) return;
        ChronoBubbleMod.CHANNEL.send(net.minecraftforge.fml.network.PacketDistributor.TRACKING_ENTITY.with(() -> p),
                new ProjectileOrbPacket(p.getId(), on));
    }

    private static void release(ProjectileEntity p) {
        CompoundNBT tag = p.getPersistentData();
        if (!tag.getBoolean(TAG_HELD)) return;
        sendOrb(p, false);
        p.setDeltaMovement(new Vector3d(tag.getDouble("chrono_bubble_mx"),
                tag.getDouble("chrono_bubble_my"), tag.getDouble("chrono_bubble_mz")));
        p.setNoGravity(tag.getBoolean("chrono_bubble_ng"));
        tag.remove(TAG_HELD);
        tag.remove("chrono_bubble_mx");
        tag.remove("chrono_bubble_my");
        tag.remove("chrono_bubble_mz");
        tag.remove("chrono_bubble_ng");
        p.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isClientSide) return;
        World world = event.world;

        // 1) esquece quem n\u00e3o est\u00e1 mais congelado
        Effect effect = BlockCloverBridge.stasisEffect();
        FROZEN.values().removeIf(e -> !e.isAlive() || effect == null || !e.hasEffect(effect));

        if (!FREEZE_PROJECTILES) return;

        // 2) zonas ligadas: proj\u00e9til que entra neste tick ou no pr\u00f3ximo (previs\u00e3o do trajeto) \u00e9 parado;
        //    quem vem r\u00e1pido fica na superf\u00edcie da zona, antes de passar.
        for (PlayerEntity o : ChronoZone.owners(world)) {
            double r = ChronoConfig.f(ChronoConfig.ZONE_RADIUS);
            double cx = o.getX(), cy = o.getY() + o.getBbHeight() * 0.5D, cz = o.getZ();
            double reach = r + ChronoConfig.ZONE_CAPTURE_REACH.get();
            AxisAlignedBB box = new AxisAlignedBB(cx - reach, cy - reach, cz - reach, cx + reach, cy + reach, cz + reach);
            for (ProjectileEntity p : world.getEntitiesOfClass(ProjectileEntity.class, box)) {
                if (HELD.containsKey(p.getId()) || p instanceof StasisOrbEntity) continue;
                if (ChronoConfig.ZONE_OWN_PASS.get() && p.getOwner() == o) continue;
                Vector3d m = p.getDeltaMovement();
                if (m.lengthSqr() < 1.0E-4D) continue;                 // cravada / parada
                double t = crossing(p.position(), m, cx, cy, cz, r);
                if (t < 0.0D) continue;
                if (t > 0.0D) {                                        // ainda fora: encosta na superf\u00edcie
                    Vector3d at = p.position().add(m.scale(t));
                    p.setPos(at.x, at.y, at.z);
                }
                hold(p);
            }
        }

        // 3) mant\u00e9m parados os que continuam dentro; solta os que ficaram sem esfera
        Iterator<ProjectileEntity> it = HELD.values().iterator();
        while (it.hasNext()) {
            ProjectileEntity p = it.next();
            if (!p.isAlive()) { it.remove(); HELD_AT.remove(p.getId()); continue; }
            if (p.level != world) continue;
            PlayerEntity owner = zoneHolding(p, world);
            if (owner != null) {
                p.setDeltaMovement(Vector3d.ZERO);
                p.hurtMarked = true;
                Long since = HELD_AT.get(p.getId());
                if (since != null && dissolves(p) && world.getGameTime() - since >= dissolveTicks() && payDissolve(owner)) {
                    it.remove();
                    HELD_AT.remove(p.getId());
                    dissolve(p);
                }
            } else {
                release(p);
                it.remove();
                HELD_AT.remove(p.getId());
            }
        }
    }

    /** Cobra a mana do dono da zona para desfazer um proj\u00e9til. Sem mana, o proj\u00e9til s\u00f3 continua parado. */
    private static boolean payDissolve(PlayerEntity owner) {
        float cost = ChronoConfig.f(ChronoConfig.ZONE_DISSOLVE_MANA);
        if (cost <= 0.0F) return true;
        if (BlockCloverBridge.getMana(owner) < cost) return false;
        BlockCloverBridge.spend(owner, cost, 0, 0);
        BlockCloverBridge.syncToClient(owner);
        return true;
    }

    /** Seguran\u00e7a: proj\u00e9til marcado que volta ao mundo (jogo foi reaberto) \u00e9 solto. */
    @SubscribeEvent
    public static void onJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isClientSide || !(event.getEntity() instanceof ProjectileEntity)) return;
        ProjectileEntity p = (ProjectileEntity) event.getEntity();
        if (p.getPersistentData().getBoolean(TAG_HELD) && !HELD.containsKey(p.getId())) release(p);
    }
}
