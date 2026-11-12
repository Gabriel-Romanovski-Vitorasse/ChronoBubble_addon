package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Zona de Chrono Stasis: ligada pela tecla (B), cria uma esfera em volta do jogador onde TODOS os
 * projéteis (de outros) ficam parados (ver StasisField). Gasta mana por segundo e some quando a
 * mana acaba, a combat bar é desativada, o jogador morre/sai, ou ele aperta a tecla de novo.
 * O liga/desliga e a cobrança de mana vêm de {@link ManaDrainAbility}.
 */
public class ChronoZone extends ManaDrainAbility {
    public static final ChronoZone INSTANCE = new ChronoZone();

    private ChronoZone() {
        super("zone");
    }

    // ---------- valores ----------

    @Override
    protected boolean enabled() {
        return ChronoConfig.ZONE_ENABLED.get();
    }

    @Override
    protected float manaPerSecond() {
        return ChronoConfig.f(ChronoConfig.ZONE_MANA_PER_SECOND);
    }

    @Override
    protected float manaToStart() {
        return ChronoConfig.f(ChronoConfig.ZONE_MANA_TO_START);
    }

    @Override
    protected String missingPowerKey() {
        return "no_stasis";
    }

    // ---------- comportamento próprio ----------

    @Override
    protected void onStarted(ServerPlayerEntity player) {
        send(player, true);
        sound(player, true);
    }

    @Override
    protected void onStopped(PlayerEntity player, boolean quiet) {
        send(player, false);
        if (!quiet) sound(player, false);
    }

    /** Aviso de "zona instável" quando restam 3 segundos ou menos de mana. */
    @Override
    protected void afterCharge(PlayerEntity player, int ticks) {
        if (BlockCloverBridge.getMana(player) / manaPerSecond() <= 3.0F) {
            Msg.bar(player, TextFormatting.RED, "zone_unstable");
            player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.8F, 0.6F);
        }
    }

    private static void sound(PlayerEntity p, boolean on) {
        p.level.playSound(null, p.getX(), p.getY(), p.getZ(),
                on ? ChronoBubbleMod.STASIS_START.get() : ChronoBubbleMod.STASIS_END.get(),
                SoundCategory.PLAYERS, 1.0F, on ? 0.8F : 1.0F);
    }

    // ---------- acesso pelas outras classes ----------

    public static boolean isActive(PlayerEntity p) {
        return INSTANCE.isRunning(p);
    }

    /** Zona ligada pela tecla (sem contar o Time Hop). */
    public static boolean hasZone(PlayerEntity p) {
        return INSTANCE.isRunning(p);
    }

    public static void toggle(ServerPlayerEntity player) {
        INSTANCE.toggleFor(player);
    }

    /** Donos de zona ligada neste mundo (servidor). */
    public static List<PlayerEntity> owners(World world) {
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : world.players()) if (p.isAlive() && INSTANCE.isRunning(p)) list.add(p);
        return list;
    }

    /** Avisa o próprio jogador e quem o vê para desenhar (ou apagar) a esfera da zona. */
    public static void send(PlayerEntity p, boolean active) {
        ChronoBubbleMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ZoneStatePacket(p.getId(), active));
    }

    // ---------- eventos próprios ----------

    /** Quem passa a ver o jogador enquanto a zona dele está ligada também recebe o estado. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof PlayerEntity) || !(event.getPlayer() instanceof ServerPlayerEntity)) return;
        PlayerEntity target = (PlayerEntity) event.getTarget();
        if (isRunning(target)) {
            ChronoBubbleMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> (ServerPlayerEntity) event.getPlayer()),
                    new ZoneStatePacket(target.getId(), true));
        }
    }

    /** Trocou de dimensão com a zona ligada: o cliente limpou o estado, então manda de novo. */
    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (isRunning(event.getPlayer())) send(event.getPlayer(), true);
    }
}
