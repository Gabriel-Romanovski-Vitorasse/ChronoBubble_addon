package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Antever ataques (servidor): olha, a cada 2 ticks, proj\u00e9teis que v\u00e3o te acertar nos pr\u00f3ximos instantes
 * e mobs hostis prestes a te atacar de perto, e avisa o cliente para desenhar o aviso.
 * Vale para quem usa magia de Tempo.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class Precognition {
    private static final double HIT_RADIUS = 1.2D;
    private static final int MAX_THREATS = 10;
    private static final Set<UUID> WAS_ACTIVE = new HashSet<>();

    /** Posi\u00e7\u00e3o prevista do proj\u00e9til daqui a {@code ticks} ticks (a mesma conta \u00e9 usada no cliente). */
    public static Vector3d predict(Vector3d pos, Vector3d motion, boolean noGravity, int ticks) {
        double x = pos.x, y = pos.y, z = pos.z, mx = motion.x, my = motion.y, mz = motion.z;
        for (int i = 0; i < ticks; i++) {
            x += mx; y += my; z += mz;
            mx *= 0.99D; my *= 0.99D; mz *= 0.99D;
            if (!noGravity) my -= 0.04D;
        }
        return new Vector3d(x, y, z);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        if (!(event.player instanceof ServerPlayerEntity) || event.player.tickCount % 2 != 0) return;
        ServerPlayerEntity player = (ServerPlayerEntity) event.player;

        List<Integer> ids = new ArrayList<>();
        List<Byte> kinds = new ArrayList<>();
        if (ChronoConfig.PRECOG_ENABLED.get() && player.isAlive() && BlockCloverBridge.isTimeUser(player)
                && (!ChronoConfig.PRECOG_REQUIRE_COMBAT.get() || BlockCloverBridge.isInCombat(player))) {
            scan(player, ids, kinds);
        }

        boolean any = !ids.isEmpty();
        if (!any && !WAS_ACTIVE.remove(player.getUUID())) return;     // nada a avisar e nada a limpar
        if (any) WAS_ACTIVE.add(player.getUUID());
        int[] a = new int[ids.size()];
        byte[] k = new byte[ids.size()];
        for (int i = 0; i < a.length; i++) { a[i] = ids.get(i); k[i] = kinds.get(i); }
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PrecogPacket(a, k));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WAS_ACTIVE.remove(event.getPlayer().getUUID());
    }

    private static void scan(ServerPlayerEntity player, List<Integer> ids, List<Byte> kinds) {
        Vector3d center = new Vector3d(player.getX(), player.getY() + player.getBbHeight() * 0.5D, player.getZ());
        net.minecraft.util.math.AxisAlignedBB box = player.getBoundingBox().inflate(ChronoConfig.PRECOG_RANGE.get());

        for (ProjectileEntity p : player.level.getEntitiesOfClass(ProjectileEntity.class, box)) {
            if (ids.size() >= MAX_THREATS) return;
            if (p.getOwner() == player || p instanceof StasisOrbEntity) continue;
            Vector3d m = p.getDeltaMovement();
            if (m.lengthSqr() < 0.04D) continue;                     // parado / cravado / segurado pela zona
            for (int t = 1; t <= ChronoConfig.PRECOG_HORIZON.get(); t++) {
                Vector3d at = predict(p.position(), m, p.isNoGravity(), t);
                if (at.distanceToSqr(center) <= HIT_RADIUS * HIT_RADIUS) {
                    ids.add(p.getId());
                    kinds.add((byte) 0);
                    break;
                }
            }
        }

        if (!ChronoConfig.PRECOG_MOBS.get()) return;
        for (MobEntity m : player.level.getEntitiesOfClass(MobEntity.class, player.getBoundingBox().inflate(6.0D))) {
            if (ids.size() >= MAX_THREATS) return;
            if (m.getTarget() == player && m.distanceToSqr(player) < 4.5D * 4.5D) {
                ids.add(m.getId());
                kinds.add((byte) 1);
            }
        }
    }
}
