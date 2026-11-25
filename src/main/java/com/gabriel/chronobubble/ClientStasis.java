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
    /** Dura\u00e7\u00e3o, em ticks, da anima\u00e7\u00e3o de a esfera "nascer" (simples) e de "sumir" (sem a quebra). */
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
            if (!active) return;
            if (ChronoConfig.BUBBLE_SHATTER.get()) {
                // acabou (sozinho ou tirado): a bolha se parte em cacos
                TIMES.remove(entityId);
                Entity e = world.getEntity(entityId);
                if (e != null) ClientShatter.burst(e);
            } else {
                // faz a esfera sumir com a anima\u00e7\u00e3o em vez de cortar de uma vez
                old[1] = Math.min(old[1], now + (long) POP_OUT_TICKS);
            }
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
            if (ChronoConfig.BUBBLE_SHATTER.get()) ClientShatter.burst(entity);   // o aviso do servidor n\u00e3o chegou
            return false;
        }
        return true;
    }

    /**
     * 0..1 (pode passar um pouco de 1 na entrada animada): tamanho relativo da esfera. Cresce ao come\u00e7ar; ao terminar
     * encolhe s\u00f3 se a quebra em cacos estiver desligada.
     */
    public static float getScale(Entity entity, float partialTicks) {
        long[] t = TIMES.get(entity.getId());
        if (t == null) return 1.0F;
        float now = entity.level.getGameTime() + partialTicks;
        float in;
        if (ChronoConfig.BUBBLE_ENTRY.get()) {
            in = BubbleFx.easeOutBack((now - t[0]) / BubbleFx.ENTRY_TICKS);
        } else {
            float p = Math.max(0.0F, Math.min(1.0F, (now - t[0]) / POP_IN_TICKS));
            in = p * p * (3.0F - 2.0F * p);
        }
        if (ChronoConfig.BUBBLE_SHATTER.get()) return in;
        float o = Math.max(0.0F, Math.min(1.0F, (t[1] - now) / POP_OUT_TICKS));
        return Math.min(in, o * o * (3.0F - 2.0F * o));
    }

    /** 0..1: quanto da entrada animada j\u00e1 passou (1 = acabou, ou entrada desligada). */
    public static float entryProgress(Entity entity, float partialTicks) {
        long[] t = TIMES.get(entity.getId());
        if (t == null || !ChronoConfig.BUBBLE_ENTRY.get()) return 1.0F;
        return BubbleFx.clamp01((entity.level.getGameTime() + partialTicks - t[0]) / BubbleFx.ENTRY_TICKS);
    }

    // ---------- ondas dos golpes ----------

    /** id da entidade -> ondas ativas: {dx, dy, dz, tick de in\u00edcio}. */
    private static final Map<Integer, java.util.List<float[]>> RIPPLES = new HashMap<>();
    private static final int MAX_RIPPLES = 6;

    /** Um golpe atingiu a bolha vindo da dire\u00e7\u00e3o (dx, dy, dz), a partir do centro dela. */
    public static void addRipple(int entityId, float dx, float dy, float dz) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null || !ChronoConfig.BUBBLE_RIPPLES.get()) return;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) return;
        java.util.List<float[]> list = RIPPLES.computeIfAbsent(entityId, k -> new java.util.ArrayList<>());
        if (list.size() >= MAX_RIPPLES) list.remove(0);
        list.add(new float[] { dx / len, dy / len, dz / len, world.getGameTime() });
    }

    /** Ondas ativas de uma entidade (esquece as que j\u00e1 acabaram), ou null. */
    public static java.util.List<float[]> ripples(Entity entity, float now) {
        java.util.List<float[]> list = RIPPLES.get(entity.getId());
        if (list == null) return null;
        list.removeIf(r -> now - r[3] >= BubbleFx.RIPPLE_TICKS);
        if (list.isEmpty()) {
            RIPPLES.remove(entity.getId());
            return null;
        }
        return list;
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        TIMES.clear();
        UNSTABLE.clear();
        RIPPLES.clear();
        ClientShatter.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        TIMES.clear();
        UNSTABLE.clear();
        RIPPLES.clear();
        ClientShatter.clear();
    }
}
