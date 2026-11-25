package com.gabriel.chronobubble;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * Servidor: quando algo golpeia um alvo congelado (soco, flecha, magia), avisa quem está vendo de que lado veio o
 * golpe, para a bolha fazer uma onda a partir desse ponto. Só visual: não muda o dano nem o congelamento.
 * Recebe também golpes cancelados (a bolha "segurou" o golpe).
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StasisRipples {
    /** Intervalo mínimo entre duas ondas na mesma entidade (ataques muito rápidos não viram enxurrada de pacotes). */
    private static final int MIN_TICKS = 2;
    private static final Map<Integer, Long> LAST = new HashMap<>();

    private StasisRipples() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity target = event.getEntityLiving();
        if (target.level.isClientSide) return;
        Effect effect = BlockCloverBridge.stasisEffect();
        boolean frozen = (effect != null && target.hasEffect(effect)) || StasisField.FROZEN.containsKey(target.getId())
                || WitherStasis.isFrozen(target);
        if (!frozen) return;
        Vector3d from = sourcePosition(event.getSource());
        if (from == null) return;
        long now = target.level.getGameTime();
        Long last = LAST.get(target.getId());
        if (last != null && now - last < MIN_TICKS) return;
        LAST.put(target.getId(), now);
        if (LAST.size() > 512) LAST.values().removeIf(t -> now - t > 200);

        Vector3d center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vector3d dir = from.subtract(center);
        if (dir.lengthSqr() < 1.0E-6D) return;
        dir = dir.normalize();
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new RipplePacket(target.getId(), (float) dir.x, (float) dir.y, (float) dir.z));
    }

    /** De onde veio o golpe: o projétil ou a entidade que bateu (olhos de quem soca, centro de quem atira). */
    private static Vector3d sourcePosition(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct instanceof LivingEntity) return direct.getEyePosition(1.0F);
        if (direct != null) return direct.position();
        Entity cause = source.getEntity();
        return cause == null ? null : cause.getEyePosition(1.0F);
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        LAST.clear();
    }
}
