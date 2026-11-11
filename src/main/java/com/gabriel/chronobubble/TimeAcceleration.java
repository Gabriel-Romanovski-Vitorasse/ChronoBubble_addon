package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Acelerar o tempo (tecla V): o jogador fica mais r\u00e1pido e ataca mais r\u00e1pido (Velocidade + Pressa) e os mobs
 * em volta ficam lentos. Custa mana por segundo e dura no m\u00e1ximo MAX_SECONDS; depois h\u00e1 uma recarga.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TimeAcceleration {
    // ===== Ajustes f\u00e1ceis (o .toml e a tela de configura\u00e7\u00e3o t\u00eam prioridade) =====
    public static boolean ENABLED = true;
    public static float MANA_PER_SECOND = 5.0F;
    public static float MANA_TO_START = 20.0F;
    /** Dura\u00e7\u00e3o m\u00e1xima em segundos (0 = at\u00e9 acabar a mana). */
    public static int MAX_SECONDS = 20;
    /** Recarga depois que termina, em ticks. */
    public static int COOLDOWN_TICKS = 100;
    /** N\u00edvel do efeito (0 = I, 1 = II, ...). */
    public static int SPEED_AMPLIFIER = 1;
    public static int HASTE_AMPLIFIER = 1;
    /** Deixa os mobs em volta lentos. */
    public static boolean SLOW_ENEMIES = true;
    public static float SLOW_RADIUS = 8.0F;
    public static int SLOW_AMPLIFIER = 1;
    /** Se true, outros jogadores tamb\u00e9m ficam lentos (PvP). */
    public static boolean SLOW_PLAYERS = false;

    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    public static void toggle(ServerPlayerEntity player) {
        if (!ENABLED) return;
        if (ACTIVE.containsKey(player.getUUID())) {
            stop(player, TextFormatting.GRAY, "accel_off");
            return;
        }
        long now = player.level.getGameTime();
        Long ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            Msg.bar(player, TextFormatting.GRAY, "accel_cooldown", Msg.f1((ready - now) / 20.0));
            return;
        }
        if (!BlockCloverBridge.hasChronoStasis(player)) {
            Msg.bar(player, TextFormatting.RED, "no_power");
            return;
        }
        if (!BlockCloverBridge.isInCombat(player)) {
            Msg.bar(player, TextFormatting.RED, "need_combat");
            return;
        }
        if (!(BlockCloverBridge.getMana(player) > MANA_TO_START)) {
            Msg.bar(player, TextFormatting.RED, "no_mana");
            return;
        }
        ACTIVE.put(player.getUUID(), 0);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_START.get(), SoundCategory.PLAYERS, 0.8F, 1.6F);
        Msg.bar(player, TextFormatting.AQUA, "accel_on", Msg.rate(MANA_PER_SECOND));
    }

    /** Termina a acelera\u00e7\u00e3o e come\u00e7a a recarga. {@code key} (pode ser null) \u00e9 a mensagem traduz\u00edvel. */
    private static void stop(PlayerEntity player, TextFormatting color, String key) {
        if (ACTIVE.remove(player.getUUID()) == null) return;
        READY_AT.put(player.getUUID(), player.level.getGameTime() + COOLDOWN_TICKS);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ChronoBubbleMod.STASIS_END.get(), SoundCategory.PLAYERS, 0.8F, 1.6F);
        if (key != null) Msg.bar(player, color, key);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        PlayerEntity player = event.player;
        Integer ticks = ACTIVE.get(player.getUUID());
        if (ticks == null) return;

        if (!player.isAlive()) { ACTIVE.remove(player.getUUID()); return; }
        if (!BlockCloverBridge.isInCombat(player)) { stop(player, TextFormatting.GRAY, "accel_combat_off"); return; }
        if (!BlockCloverBridge.hasChronoStasis(player)) { stop(player, null, null); return; }

        ticks++;
        ACTIVE.put(player.getUUID(), ticks);

        if (ticks % 20 == 0) {
            if (BlockCloverBridge.getMana(player) < MANA_PER_SECOND) {
                stop(player, TextFormatting.RED, "accel_no_mana");
                return;
            }
            BlockCloverBridge.spend(player, MANA_PER_SECOND, 0, 0);
            if (player instanceof ServerPlayerEntity) BlockCloverBridge.syncToClient(player);
            if (MAX_SECONDS > 0 && ticks / 20 >= MAX_SECONDS) {
                stop(player, TextFormatting.GRAY, "accel_limit");
                return;
            }
            if (MAX_SECONDS > 0) Msg.bar(player, TextFormatting.AQUA, "accel_left", Math.max(0, MAX_SECONDS - ticks / 20));
            else Msg.bar(player, TextFormatting.AQUA, "accel_active");
        }

        if (ticks % 10 == 0) {
            // efeitos curtos, renovados a cada 10 ticks (somem sozinhos logo ap\u00f3s o fim)
            player.addEffect(new EffectInstance(Effects.MOVEMENT_SPEED, 25, SPEED_AMPLIFIER, false, false, false));
            player.addEffect(new EffectInstance(Effects.DIG_SPEED, 25, HASTE_AMPLIFIER, false, false, false));
            if (SLOW_ENEMIES) {
                final double r = SLOW_RADIUS;
                for (LivingEntity e : player.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
                        x -> x != player && x.isAlive() && x.distanceToSqr(player) <= r * r)) {
                    if (e instanceof PlayerEntity && !SLOW_PLAYERS) continue;
                    if (e instanceof TameableEntity && ((TameableEntity) e).getOwnerUUID() != null
                            && ((TameableEntity) e).getOwnerUUID().equals(player.getUUID())) continue;
                    e.addEffect(new EffectInstance(Effects.MOVEMENT_SLOWDOWN, 25, SLOW_AMPLIFIER, false, false, false));
                }
            }
        }
        if (ticks % 4 == 0 && player.level instanceof ServerWorld) {
            ((ServerWorld) player.level).sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 0.9D, player.getZ(), 3, 0.35D, 0.6D, 0.35D, 0.4D);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity) ACTIVE.remove(event.getEntityLiving().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getPlayer().getUUID());
        ChronoThrowHandler.forgetExpired(READY_AT, event.getPlayer().getUUID(), event.getPlayer().level.getGameTime());
    }

    @SubscribeEvent
    public static void onServerStopping(FMLServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
