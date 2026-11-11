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
import java.util.Map;
import java.util.Set;

/** Cliente: quais jogadores est\u00e3o com o grim\u00f3rio de uma p\u00e1gina, e a anima\u00e7\u00e3o de "as p\u00e1ginas voltam". */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRebirth {
    /** Dura\u00e7\u00e3o, em ticks, da anima\u00e7\u00e3o em que as p\u00e1ginas reaparecem. */
    public static final float RESTORE_TICKS = 40.0F;

    private static final Set<Integer> REBORN = new HashSet<>();
    private static final Map<Integer, Long> RESTORED_AT = new HashMap<>();

    public static void set(int entityId, boolean reborn) {
        ClientWorld world = Minecraft.getInstance().level;
        if (reborn) {
            REBORN.add(entityId);
            RESTORED_AT.remove(entityId);
        } else if (REBORN.remove(entityId) && world != null) {
            RESTORED_AT.put(entityId, world.getGameTime());
        }
    }

    /** Quantas p\u00e1ginas desenhar (de 1 at\u00e9 {@code full}). */
    public static int pageCount(Entity entity, float partialTicks, int full) {
        if (REBORN.contains(entity.getId())) return 1;
        Long at = RESTORED_AT.get(entity.getId());
        if (at == null) return full;
        float p = (entity.level.getGameTime() + partialTicks - at) / RESTORE_TICKS;
        if (p >= 1.0F) {
            RESTORED_AT.remove(entity.getId());
            return full;
        }
        float eased = p * p * (3.0F - 2.0F * p);
        return Math.max(1, Math.round(full * Math.max(0.0F, eased)));
    }

    /**
     * Trocou de dimens\u00e3o ou renasceu: os ids de entidade do mundo novo n\u00e3o t\u00eam rela\u00e7\u00e3o com os antigos, ent\u00e3o
     * o que estava guardado por id viraria esfera fantasma em outra entidade. O servidor reenvia o que vale.
     */
    @SubscribeEvent
    public static void onRespawn(ClientPlayerNetworkEvent.RespawnEvent event) {
        REBORN.clear();
        RESTORED_AT.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        REBORN.clear();
        RESTORED_AT.clear();
    }
}
