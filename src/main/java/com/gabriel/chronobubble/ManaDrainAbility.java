package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base das habilidades "liga/desliga que gastam mana por segundo" (zona de Chrono Stasis, aceleração do tempo).
 * <p>
 * Aqui fica tudo o que elas têm em comum: as checagens para ligar (tem o poder, combat bar ativa, mana mínima,
 * recarga), a cobrança a cada segundo, e o desligamento automático (combat bar desligada, mana acabou, perdeu o
 * poder, morreu, saiu do jogo, servidor fechou). Cada subclasse só diz os seus valores e o que fazer de especial
 * ao ligar, a cada tick e ao desligar.
 * <p>
 * As mensagens seguem o prefixo da subclasse: {@code <prefixo>_on}, {@code _off}, {@code _cooldown},
 * {@code _combat_off} e {@code _no_mana} (ver os arquivos de idioma).
 * <p>
 * Cada subclasse tem uma única instância, registrada no barramento de eventos do Forge pelo construtor do mod.
 */
public abstract class ManaDrainAbility {
    /** Jogadores com a habilidade ligada -> ticks desde que ligou. */
    private final Map<UUID, Integer> active = new HashMap<>();
    /** Quando cada jogador pode ligar de novo (tempo do mundo). */
    private final Map<UUID, Long> readyAt = new HashMap<>();
    private final String msg;

    protected ManaDrainAbility(String messagePrefix) {
        this.msg = messagePrefix;
    }

    // ---------- o que cada habilidade define ----------

    protected abstract boolean enabled();

    protected abstract float manaPerSecond();

    /** Mana mínima para ligar (precisa ter MAIS que isso). */
    protected abstract float manaToStart();

    /** Mensagem quando o jogador não tem o poder (ex.: está renascido). */
    protected abstract String missingPowerKey();

    /** Recarga depois de desligar, em ticks. 0 = sem recarga. */
    protected int cooldownTicks() {
        return 0;
    }

    /** Acabou de ligar (sons, pacotes para os clientes...). */
    protected void onStarted(ServerPlayerEntity player) {
    }

    /** Acabou de desligar. {@code quiet} = desligou porque morreu/saiu (sem som nem mensagem). */
    protected void onStopped(PlayerEntity player, boolean quiet) {
    }

    /** A cada tick enquanto ligada (depois da cobrança, se houve). */
    protected void activeTick(PlayerEntity player, int ticks) {
    }

    /** Logo depois de cobrar um segundo de mana (pode chamar {@link #stop}). */
    protected void afterCharge(PlayerEntity player, int ticks) {
    }

    // ---------- comum a todas ----------

    public boolean isRunning(PlayerEntity player) {
        return active.containsKey(player.getUUID());
    }

    /** Tecla apertada: liga se puder, desliga se já estiver ligada. */
    public void toggleFor(ServerPlayerEntity player) {
        if (!enabled()) return;
        if (isRunning(player)) {
            stop(player, TextFormatting.GRAY, msg + "_off");
            return;
        }
        long now = player.level.getGameTime();
        Long ready = readyAt.get(player.getUUID());
        if (ready != null && now < ready) {
            Msg.bar(player, TextFormatting.GRAY, msg + "_cooldown", Msg.f1((ready - now) / 20.0));
            return;
        }
        if (!BlockCloverBridge.hasChronoStasis(player)) {
            Msg.bar(player, TextFormatting.RED, missingPowerKey());
            return;
        }
        if (!BlockCloverBridge.isInCombat(player)) {
            Msg.bar(player, TextFormatting.RED, "need_combat");
            return;
        }
        if (!(BlockCloverBridge.getMana(player) > manaToStart())) {
            Msg.bar(player, TextFormatting.RED, "no_mana");
            return;
        }
        active.put(player.getUUID(), 0);
        onStarted(player);
        Msg.bar(player, TextFormatting.AQUA, msg + "_on", Msg.rate(manaPerSecond()));
    }

    /** Desliga e começa a recarga. {@code key} (pode ser null) é a mensagem traduzível da action bar. */
    protected void stop(PlayerEntity player, TextFormatting color, String key) {
        if (active.remove(player.getUUID()) == null) return;
        if (cooldownTicks() > 0) readyAt.put(player.getUUID(), player.level.getGameTime() + cooldownTicks());
        onStopped(player, false);
        if (key != null) Msg.bar(player, color, key);
    }

    /** Desliga sem recarga, som ou mensagem (morte, saída do jogo). */
    private void forget(PlayerEntity player) {
        if (active.remove(player.getUUID()) != null) onStopped(player, true);
    }

    /**
     * Cobra um segundo de mana. Devolve false (e não cobra) se a mana não dá.
     * Também usado pelo Chrono Stasis sustentado, que cobra por alvo.
     */
    public static boolean chargeSecond(PlayerEntity player, float rate) {
        if (BlockCloverBridge.getMana(player) < rate) return false;
        BlockCloverBridge.spend(player, rate, 0, 0);
        if (player instanceof ServerPlayerEntity) BlockCloverBridge.syncToClient(player);
        return true;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) return;
        PlayerEntity player = event.player;
        Integer ticks = active.get(player.getUUID());
        if (ticks == null) return;

        if (!player.isAlive()) { forget(player); return; }
        if (!BlockCloverBridge.isInCombat(player)) { stop(player, TextFormatting.GRAY, msg + "_combat_off"); return; }
        if (!BlockCloverBridge.hasChronoStasis(player)) { stop(player, null, null); return; }   // ex.: renascido

        ticks++;
        active.put(player.getUUID(), ticks);
        if (ticks % 20 == 0) {
            if (!chargeSecond(player, manaPerSecond())) {
                stop(player, TextFormatting.RED, msg + "_no_mana");
                return;
            }
            afterCharge(player, ticks);
            if (!isRunning(player)) return;
        }
        activeTick(player, ticks);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof ServerPlayerEntity) forget((PlayerEntity) event.getEntityLiving());
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerEntity player = event.getPlayer();
        forget(player);
        ChronoThrowHandler.forgetExpired(readyAt, player.getUUID(), player.level.getGameTime());
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        active.clear();
    }
}
