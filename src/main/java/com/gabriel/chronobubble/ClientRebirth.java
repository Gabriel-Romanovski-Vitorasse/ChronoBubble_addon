package com.gabriel.chronobubble;

import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente: quantas p\u00e1ginas o grim\u00f3rio de cada jogador renascido tem (1, 4, 16 ou 64), e a anima\u00e7\u00e3o das p\u00e1ginas
 * voltando quando ele passa de fase ou fica completo.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRebirth {
    /** Dura\u00e7\u00e3o, em ticks, da anima\u00e7\u00e3o em que as p\u00e1ginas reaparecem. */
    public static final float RESTORE_TICKS = 40.0F;
    /** Nas anima\u00e7\u00f5es, "grim\u00f3rio completo" (o n\u00famero real de p\u00e1ginas s\u00f3 o renderizador sabe). */
    private static final int ALL = -1;

    private static final class Anim {
        final int from, to;
        final long at;

        Anim(int from, int to, long at) {
            this.from = from;
            this.to = to;
            this.at = at;
        }
    }

    /** P\u00e1ginas de quem est\u00e1 renascido; quem n\u00e3o est\u00e1 aqui tem o grim\u00f3rio completo. */
    private static final Map<Integer, Integer> PAGES = new HashMap<>();
    private static final Map<Integer, Anim> ANIM = new HashMap<>();

    public static void set(int entityId, int pages) {
        ClientWorld world = Minecraft.getInstance().level;
        Integer old = PAGES.get(entityId);
        int from = old == null ? ALL : old;
        int to = pages == GrimoireStages.FULL ? ALL : pages;
        if (to == ALL) PAGES.remove(entityId);
        else PAGES.put(entityId, to);
        // anima s\u00f3 quando as p\u00e1ginas aumentam; ao perder o grim\u00f3rio (renascer), a troca \u00e9 na hora
        boolean grows = from != ALL && (to == ALL || to > from);
        if (grows && world != null) ANIM.put(entityId, new Anim(from, to, world.getGameTime()));
        else ANIM.remove(entityId);
    }

    /** Quantas p\u00e1ginas desenhar (de 1 at\u00e9 {@code full}). */
    public static int pageCount(Entity entity, float partialTicks, int full) {
        Integer now = PAGES.get(entity.getId());
        int target = now == null ? full : Math.min(now, full);
        Anim a = ANIM.get(entity.getId());
        if (a == null) return target;
        float p = (entity.level.getGameTime() + partialTicks - a.at) / RESTORE_TICKS;
        if (p >= 1.0F) {
            ANIM.remove(entity.getId());
            return target;
        }
        int from = a.from == ALL ? full : Math.min(a.from, full);
        int to = a.to == ALL ? full : Math.min(a.to, full);
        float eased = p * p * (3.0F - 2.0F * Math.max(0.0F, p));
        return Math.max(1, Math.round(from + (to - from) * Math.max(0.0F, eased)));
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        PAGES.clear();
        ANIM.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        PAGES.clear();
        ANIM.clear();
    }
}
