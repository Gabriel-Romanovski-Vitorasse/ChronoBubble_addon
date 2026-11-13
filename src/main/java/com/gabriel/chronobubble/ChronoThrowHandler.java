package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chrono Stasis arremess\u00e1vel: ao acionar o Chrono Stasis segurando Shift, o soco \u00e9 cancelado
 * e uma esfera \u00e9 lan\u00e7ada no lugar. Mesmo slot, mesmas regras de custo do soco original.
 */
public class ChronoThrowHandler {

    // --- Time Steal arremess\u00e1vel ---
    private static final String STEAL_CLASS = BlockCloverBridge.CLS_STEAL;

    private static final String HOP_CLASS = BlockCloverBridge.CLS_HOP;
    private static final String GRIGORA_CLASS = BlockCloverBridge.CLS_GRIGORA;
    private static final String ABILITY_CLASS = BlockCloverBridge.CLS_STASIS;
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static final Map<UUID, Long> STEAL_READY_AT = new HashMap<>();

    /** Escuta o AbilityUseEvent.Pre do Block Clover sem precisar do .jar dele para compilar. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ChronoThrowHandler::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(ChronoThrowHandler::onLogout);
        try {
            // classe base: recebe o evento das habilidades de soco e o Pre/Post das comuns (ver o Bridge)
            Class<?> pre = Class.forName(BlockCloverBridge.CLS_ABILITY_USE_EVENT, false, ChronoThrowHandler.class.getClassLoader());
            Consumer<Event> handler = ChronoThrowHandler::onAbilityUse;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, (Class) pre, (Consumer) handler);
            LOGGER.info("[ChronoBubble] Escuta de " + pre.getSimpleName() + " registrada.");
        } catch (Throwable t) {
            LOGGER.warn("[ChronoBubble] Nao consegui registrar o arremesso: " + t);
            // Block Clover ausente ou diferente: o arremesso simplesmente n\u00e3o existe
        }
    }

    private static String cooldownBar(long remaining, String label) {
        int total = 10;
        int filled = (int) Math.max(0, Math.min(total, total - Math.round(remaining * (double) total / ChronoConfig.THROW_COOLDOWN_TICKS.get())));
        StringBuilder b = new StringBuilder("\u00a73" + label + " \u00a78[\u00a7b");
        for (int i = 0; i < total; i++) { if (i == filled) b.append("\u00a77"); b.append("|"); }
        return b.append("\u00a78] \u00a77").append(String.format("%.1fs", remaining / 20.0)).toString();
    }

    /**
     * Esquece as recargas j\u00e1 vencidas de quem saiu (sen\u00e3o os mapas s\u00f3 crescem num servidor aberto por muito
     * tempo). Recarga ainda correndo fica guardada, para sair e entrar de novo n\u00e3o zerar a espera.
     */
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        long now = event.getPlayer().level.getGameTime();
        forgetExpired(READY_AT, event.getPlayer().getUUID(), now);
        forgetExpired(STEAL_READY_AT, event.getPlayer().getUUID(), now);
    }

    /** Remove a recarga de {@code id} se ela j\u00e1 terminou. */
    static void forgetExpired(Map<UUID, Long> map, UUID id, long now) {
        Long ready = map.get(id);
        if (ready != null && now >= ready) map.remove(id);
    }

    /** A cada tick do servidor: mostra a recarga andando e avisa (com som) quando fica pronto. */
    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        tickCooldown(event.player, READY_AT, "Chrono Stasis");
        tickCooldown(event.player, STEAL_READY_AT, "Time Steal");
    }

    private static void tickCooldown(PlayerEntity player, Map<UUID, Long> map, String label) {
        Long ready = map.get(player.getUUID());
        if (ready == null) return;
        long now = player.level.getGameTime();
        if (now >= ready) {
            map.remove(player.getUUID());
            Msg.bar(player, TextFormatting.AQUA, "ready", label);
            player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.6F, 1.8F);
        } else if ((ready - now) % 4 == 0) {
            player.displayClientMessage(new StringTextComponent(cooldownBar(ready - now, label)), true);
        }
    }

    /**
     * O addon age neste evento de uso? Sim para o evento base (habilidades de soco: Chrono Stasis, Time Steal) e
     * para o Pre (habilidades comuns: Time Hop, Grigora); n\u00e3o para o Post, que chega depois de a habilidade agir.
     * Escutar s\u00f3 o Pre quebrava os arremessos, porque as habilidades de soco nunca disparam Pre.
     */
    static boolean actsOn(Object abilityUseEvent) {
        return !abilityUseEvent.getClass().getName().endsWith(BlockCloverBridge.ABILITY_USE_POST_SUFFIX);
    }

    private static void onAbilityUse(Event event) {
        if (!ChronoConfig.THROW_ENABLED.get()) return;
        if (!(event instanceof PlayerEvent)) return;
        if (!actsOn(event)) return;
        PlayerEntity player = ((PlayerEvent) event).getPlayer();
        if (player.level.isClientSide || !(player instanceof ServerPlayerEntity)) return;
        String name = BlockCloverBridge.abilityClassName(event);
        boolean stasis = ABILITY_CLASS.equals(name);
        boolean steal = STEAL_CLASS.equals(name);
        boolean grigora = GRIGORA_CLASS.equals(name);
        if (BlockCloverBridge.CLS_HEAL.equals(name)) {   // Shift + Time Healing = Restaura\u00e7\u00e3o do tempo
            if (!ChronoConfig.REST_ENABLED.get() || !player.isShiftKeyDown()) return;
            try {
                event.setCanceled(true);
            } catch (Throwable t) {
                return;                                 // n\u00e3o deu para cancelar: fica a cura normal
            }
            TimeRestoration.start((ServerPlayerEntity) player);
            return;
        }
        if (HOP_CLASS.equals(name)) {
            if (ChronoConfig.REHOP_ENABLED.get()) {     // Time Hop reformulado: troca o teletransporte pelo deslize
                try {
                    event.setCanceled(true);
                } catch (Throwable t) {
                    TimeHopEffects.noteUse((ServerPlayerEntity) player);   // n\u00e3o deu para cancelar: fica o original
                    return;
                }
                TimeHop.start((ServerPlayerEntity) player);
            } else {                                    // Time Hop original: s\u00f3 acrescenta os efeitos
                TimeHopEffects.noteUse((ServerPlayerEntity) player);
            }
            return;
        }
        if (!stasis && !steal && !grigora) return;
        LOGGER.info("[ChronoBubble] " + name + " usado. Shift=" + player.isShiftKeyDown());

        if (grigora) {                                  // Alt + Grigora = vers\u00e3o sustentada em \u00e1rea
            if (!ChronoConfig.GRIGORA_ENABLED.get() || !SustainedStasis.holdsTrigger(player)) return;
            try {
                event.setCanceled(true);
            } catch (Throwable t) {
                return;
            }
            SustainedStasis.beginArea((ServerPlayerEntity) player);
            return;
        }

        if (!player.isShiftKeyDown()) {
            if (stasis) TimeAging.armPunch((ServerPlayerEntity) player);   // soco normal: o tempo do alvo pode avan\u00e7ar
            return;
        }
        try {
            event.setCanceled(true);                    // cancela o soco: aqui \u00e9 o arremesso
        } catch (Throwable t) {
            return;                                     // evento n\u00e3o cancel\u00e1vel: deixa o soco normal
        }
        if (steal) throwSteal((ServerPlayerEntity) player); else throwOrb((ServerPlayerEntity) player);
    }

    /** Time Steal arremess\u00e1vel: a esfera, ao acertar, drena vida do alvo e a converte em Time para o dono. */
    private static void throwSteal(ServerPlayerEntity player) {
        long now = player.level.getGameTime();
        Long ready = STEAL_READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            player.displayClientMessage(new StringTextComponent(cooldownBar(ready - now, "Time Steal")), true);
            return;
        }
        if (!(BlockCloverBridge.getMana(player) > ChronoConfig.f(ChronoConfig.STEAL_MANA_REQUIRED))) {
            Msg.bar(player, TextFormatting.RED, "no_mana");
            return;
        }
        if (ChronoConfig.MAX_TIME.get() > 0 && BlockCloverBridge.getTime(player) >= ChronoConfig.MAX_TIME.get()) {
            Msg.bar(player, TextFormatting.GRAY, "time_full");
            return;
        }
        STEAL_READY_AT.put(player.getUUID(), now + ChronoConfig.THROW_COOLDOWN_TICKS.get());
        BlockCloverBridge.spend(player, ChronoConfig.f(ChronoConfig.STEAL_MANA_COST), 0, ChronoConfig.STEAL_EXPERIENCE.get());
        BlockCloverBridge.syncToClient(player);

        StasisOrbEntity orb = new StasisOrbEntity(ChronoBubbleMod.STASIS_ORB.get(), player.level, player);
        orb.steal = true;
        orb.shootFromRotation(player, player.xRot, player.yRot, 0.0F, ChronoConfig.f(ChronoConfig.THROW_SPEED), 0.5F);
        player.level.addFreshEntity(orb);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundCategory.PLAYERS, 0.8F, 0.8F);
    }

    /** Chamado pela esfera de Time Steal ao acertar: vida do alvo vira Time do dono. */
    public static void stealLife(ServerPlayerEntity owner, net.minecraft.entity.Entity orb, net.minecraft.entity.LivingEntity target) {
        // Wither: drena menos vida, mas cada ponto rende mais Time ("vida de chefe \u00e9 tempo concentrado")
        boolean wither = WitherStasis.applies(target);
        if (wither && ((net.minecraft.entity.boss.WitherEntity) target).getInvulnerableTicks() > 0) {
            Msg.bar(owner, TextFormatting.GRAY, "wither_invulnerable");     // ainda nascendo: n\u00e3o recebe dano
            return;
        }
        float drain = ChronoConfig.f(ChronoConfig.STEAL_DRAIN_HP) * (wither ? ChronoConfig.f(ChronoConfig.WITHER_STEAL_DRAIN) : 1.0F);
        float perHp = ChronoConfig.f(ChronoConfig.STEAL_TIME_PER_HP) * (wither ? ChronoConfig.f(ChronoConfig.WITHER_STEAL_TIME_MULT) : 1.0F);
        float before = target.getHealth();
        SustainedStasis.hurtDirect(target, net.minecraft.util.DamageSource.indirectMagic(orb, owner), Math.min(drain, before));
        float lost = Math.max(0.0F, before - Math.max(0.0F, target.getHealth()));
        int room = ChronoConfig.MAX_TIME.get() > 0 ? ChronoConfig.MAX_TIME.get() - Math.max(0, BlockCloverBridge.getTime(owner)) : Integer.MAX_VALUE;
        int gain = Math.min(room, Math.round(lost * perHp));
        if (gain > 0) {
            BlockCloverBridge.spend(owner, 0.0F, -gain, 0);          // tempo negativo = ganha Time
            BlockCloverBridge.syncToClient(owner);
            RebirthHandler.acceptTime(owner);
        }
        if (target.level instanceof net.minecraft.world.server.ServerWorld) {
            ((net.minecraft.world.server.ServerWorld) target.level).sendParticles(net.minecraft.particles.ParticleTypes.SOUL,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 14, 0.3D, 0.4D, 0.3D, 0.05D);
        }
        target.level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.9F, 1.4F);
        Msg.bar(owner, TextFormatting.AQUA, "steal_gain", gain, Msg.f1(lost));
    }

    private static void throwOrb(ServerPlayerEntity player) {
        long now = player.level.getGameTime();
        Long ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {             // em recarga
            player.displayClientMessage(new StringTextComponent(cooldownBar(ready - now, "Chrono Stasis")), true);
            return;
        }

        if (!(BlockCloverBridge.getMana(player) > ChronoConfig.f(ChronoConfig.THROW_MANA_REQUIRED)) || !(BlockCloverBridge.getTime(player) > ChronoConfig.THROW_TIME_REQUIRED.get())) {
            Msg.bar(player, TextFormatting.RED, "no_mana_or_time");
            return;
        }
        READY_AT.put(player.getUUID(), now + ChronoConfig.THROW_COOLDOWN_TICKS.get());
        BlockCloverBridge.spend(player, ChronoConfig.f(ChronoConfig.THROW_MANA_COST), ChronoConfig.THROW_TIME_COST.get(), ChronoConfig.THROW_EXPERIENCE.get());
        BlockCloverBridge.syncToClient(player);

        StasisOrbEntity orb = new StasisOrbEntity(ChronoBubbleMod.STASIS_ORB.get(), player.level, player);
        orb.sustained = ChronoConfig.SUSTAIN_ENABLED.get() && SustainedStasis.holdsTrigger(player);
        orb.shootFromRotation(player, player.xRot, player.yRot, 0.0F, ChronoConfig.f(ChronoConfig.THROW_SPEED), 0.5F);
        player.level.addFreshEntity(orb);
        player.level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_PEARL_THROW, SoundCategory.PLAYERS, 0.8F, 1.3F);
    }
}
