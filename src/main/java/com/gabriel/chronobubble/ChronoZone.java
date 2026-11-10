package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zona de Chrono Stasis: ligada pela tecla (B), cria uma esfera em volta do jogador onde TODOS os
 * proj\u00e9teis (de outros) ficam parados (ver StasisField). Gasta mana por segundo e some quando a
 * mana acaba, a combat bar \u00e9 desativada, o jogador morre/sai, ou ele aperta a tecla de novo.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChronoZone {
    // ===== Ajustes f\u00e1ceis =====
    public static boolean ENABLED = true;
    /** Raio da zona, em blocos. */
    public static float RADIUS = 7.0F;
    /** Mana gasta por segundo enquanto a zona est\u00e1 ligada. */
    public static float MANA_PER_SECOND = 3.0F;
    /** Mana m\u00ednima (acima disso) para ligar a zona. */
    public static float MANA_TO_START = 20.0F;
    /** Se true, as flechas/magias do pr\u00f3prio dono atravessam a zona normalmente. */
    public static boolean OWN_PROJECTILES_PASS = true;
    /** Desenha um orbe do Chrono Stasis em cada proj\u00e9til parado pela zona. */
    public static boolean PROJECTILE_ORBS = true;

    /** Jogadores com a zona ligada -> ticks desde que ligou. */
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public static boolean isActive(PlayerEntity p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Zona ligada pela tecla (sem contar o Time Hop). */
    public static boolean hasZone(PlayerEntity p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    /** Donos de zona ligada neste mundo (servidor). */
    public static List<PlayerEntity> owners(World world) {
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : world.players()) if (p.isAlive() && ACTIVE.containsKey(p.getUUID())) list.add(p);
        return list;
    }

    public static void send(PlayerEntity p, boolean active) {
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ZoneStatePacket(p.getId(), active));
    }

    private static void sound(PlayerEntity p, boolean on) {
        p.level.playSound(null, p.getX(), p.getY(), p.getZ(),
                on ? ChronoBubbleMod.STASIS_START.get() : ChronoBubbleMod.STASIS_END.get(),
                SoundCategory.PLAYERS, 1.0F, on ? 0.8F : 1.0F);
    }

    public static void toggle(ServerPlayerEntity player) {
        if (!ENABLED) return;
        if (ACTIVE.containsKey(player.getUUID())) {
            stop(player, "\u00a77Zona de Chrono Stasis desligada.");
            return;
        }
        if (!BlockCloverBridge.hasChronoStasis(player)) {
            player.displayClientMessage(new StringTextComponent("\u00a7cVoc\u00ea n\u00e3o tem o Chrono Stasis."), true);
            return;
        }
        if (!BlockCloverBridge.isInCombat(player)) {
            player.displayClientMessage(new StringTextComponent("\u00a7cAtive a combat bar primeiro."), true);
            return;
        }
        if (!(BlockCloverBridge.getMana(player) > MANA_TO_START)) {
            player.displayClientMessage(new StringTextComponent("\u00a7cMana insuficiente."), true);
            return;
        }
        ACTIVE.put(player.getUUID(), 0);
        send(player, true);
        sound(player, true);
        player.displayClientMessage(new StringTextComponent("\u00a7bZona de Chrono Stasis \u00a77(" + (int) MANA_PER_SECOND + " mana/s)"), true);
    }

    private static void stop(PlayerEntity player, String message) {
        if (ACTIVE.remove(player.getUUID()) == null) return;
        send(player, false);
        sound(player, false);
        if (message != null) player.displayClientMessage(new StringTextComponent(message), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        PlayerEntity player = event.player;
        Integer ticks = ACTIVE.get(player.getUUID());
        if (ticks == null) return;

        if (!player.isAlive()) { ACTIVE.remove(player.getUUID()); return; }
        if (!BlockCloverBridge.isInCombat(player)) { stop(player, "\u00a77Combat bar desativada: zona desligada."); return; }
        if (!BlockCloverBridge.hasChronoStasis(player)) { stop(player, null); return; }   // ex.: renascido

        ticks++;
        ACTIVE.put(player.getUUID(), ticks);
        if (ticks % 20 == 0) {
            if (BlockCloverBridge.getMana(player) < MANA_PER_SECOND) {
                stop(player, "\u00a7cSem mana: a zona se desfez.");
                return;
            }
            BlockCloverBridge.spend(player, MANA_PER_SECOND, 0, 0);
            if (player instanceof ServerPlayerEntity) BlockCloverBridge.syncToClient(player);
            if (BlockCloverBridge.getMana(player) / MANA_PER_SECOND <= 3.0F) {
                player.displayClientMessage(new StringTextComponent("\u00a7c\u26a0 Zona inst\u00e1vel \u2014 mana acabando"), true);
                player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8F, 0.6F);
            }
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity) {
            PlayerEntity p = (PlayerEntity) event.getEntityLiving();
            if (ACTIVE.remove(p.getUUID()) != null) send(p, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getPlayer().getUUID());
    }

    /** Quem passa a ver o jogador enquanto a zona dele est\u00e1 ligada tamb\u00e9m recebe o estado. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof PlayerEntity) || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        PlayerEntity target = (PlayerEntity) event.getTarget();
        if (ACTIVE.containsKey(target.getUUID())) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()),
                    new ZoneStatePacket(target.getId(), true));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
