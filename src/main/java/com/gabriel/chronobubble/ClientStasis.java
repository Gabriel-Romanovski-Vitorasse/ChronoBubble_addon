package com.gabriel.chronobubble;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;

/** Lado do cliente: guarda quais entidades est\u00e3o congeladas (tick de in\u00edcio e de fim no mundo). */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientStasis {
    /** Dura\u00e7\u00e3o, em ticks, da anima\u00e7\u00e3o de a esfera "nascer" e de "sumir". */
    public static final float POP_IN_TICKS = 6.0F;
    public static final float POP_OUT_TICKS = 8.0F;

    /** id da entidade -> {tick de in\u00edcio, tick de fim} */
    private static final Map<Integer, long[]> TIMES = new HashMap<>();

    public static void set(int entityId, int duration) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null) return;
        long now = world.getGameTime();
        long[] old = TIMES.get(entityId);
        boolean active = old != null && old[1] > now;
        if (duration <= 0) {
            // removido antes da hora: faz a esfera sumir com a anima\u00e7\u00e3o em vez de cortar de uma vez
            if (active) old[1] = Math.min(old[1], now + (long) POP_OUT_TICKS);
            return;
        }
        long start = active ? old[0] : now;       // renovar o efeito n\u00e3o reinicia a anima\u00e7\u00e3o
        TIMES.put(entityId, new long[] { start, now + duration });
    }

    private static final Set<Integer> UNSTABLE = new HashSet<>();

    public static void setUnstable(int entityId, boolean unstable) {
        if (unstable) UNSTABLE.add(entityId); else UNSTABLE.remove(entityId);
    }

    /** 1 = esfera est\u00e1vel; piscando entre ~0.25 e 1 quando a mana do Julius est\u00e1 acabando. */
    public static float flicker(Entity entity, float time) {
        if (!UNSTABLE.contains(entity.getId())) return 1.0F;
        return 0.25F + 0.75F * Math.abs((float) Math.sin(time * 0.55F));
    }

    public static boolean isFrozen(Entity entity) {
        long[] t = TIMES.get(entity.getId());
        if (t == null) return false;
        if (entity.level.getGameTime() >= t[1]) {
            TIMES.remove(entity.getId());
            return false;
        }
        return true;
    }

    /** 0..1: tamanho relativo da esfera (cresce ao come\u00e7ar, encolhe ao terminar). */
    public static float getScale(Entity entity, float partialTicks) {
        long[] t = TIMES.get(entity.getId());
        if (t == null) return 1.0F;
        float now = entity.level.getGameTime() + partialTicks;
        float in = Math.max(0.0F, Math.min(1.0F, (now - t[0]) / POP_IN_TICKS));
        float out = Math.max(0.0F, Math.min(1.0F, (t[1] - now) / POP_OUT_TICKS));
        float s = Math.min(in, out);
        return s * s * (3.0F - 2.0F * s);          // suaviza (smoothstep)
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        TIMES.clear();
        UNSTABLE.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        TIMES.clear();
        UNSTABLE.clear();
    }
}
