package com.gabriel.chronobubble;

import net.minecraft.advancements.Advancement;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ResourceLocation;

/**
 * Conquistas do addon (data/chrono_bubble/advancements). As que não têm um gatilho do jogo usam o critério
 * "minecraft:impossible" e são dadas por código aqui.
 */
public final class ChronoAdvancements {
    public static final String FULL_SWALLOWTAIL = "full_swallowtail";
    public static final String REBIRTH = "rebirth";

    private ChronoAdvancements() {
    }

    /** Dá a conquista {@code id} ao jogador (no servidor; repetir não faz nada). */
    public static void grant(PlayerEntity player, String id) {
        if (!(player instanceof ServerPlayerEntity) || player.getServer() == null) return;
        ServerPlayerEntity sp = (ServerPlayerEntity) player;
        Advancement adv = sp.getServer().getAdvancements().getAdvancement(new ResourceLocation(ChronoBubbleMod.MODID, id));
        if (adv == null) return;
        for (String criterion : sp.getAdvancements().getOrStartProgress(adv).getRemainingCriteria()) {
            sp.getAdvancements().award(adv, criterion);
        }
    }
}
