package com.gabriel.chronobubble;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Configura\u00e7\u00e3o: config/chrono_bubble-common.toml (criado na primeira vez que o jogo abre) e tela dentro do jogo
 * (Mods > Chrono Bubble > Config, ou a tecla K). As op\u00e7\u00f5es novas s\u00e3o adicionadas ao arquivo antigo automaticamente.
 * Em servidor dedicado, valem as regras do arquivo que est\u00e1 no servidor.
 */
@Mod.EventBusSubscriber(modid = ChronoBubbleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChronoConfig {
    public static final ForgeConfigSpec SPEC;

    /** Descri\u00e7\u00e3o de uma op\u00e7\u00e3o, usada pela tela de configura\u00e7\u00e3o. */
    public static final class Opt {
        public static final int BOOL = 0, INT = 1, DOUBLE = 2;
        public final String sectionTitle, key, label, comment;
        public final int kind;
        public final double min, max;
        public final Object def;
        public final ForgeConfigSpec.ConfigValue<?> value;

        Opt(String sectionTitle, String key, String label, String comment, int kind, double min, double max,
            Object def, ForgeConfigSpec.ConfigValue<?> value) {
            this.sectionTitle = sectionTitle; this.key = key; this.label = label; this.comment = comment;
            this.kind = kind; this.min = min; this.max = max; this.def = def; this.value = value;
        }
    }

    /** Todas as op\u00e7\u00f5es, na ordem em que aparecem na tela. */
    public static final List<Opt> OPTS = new ArrayList<>();
    private static String curTitle = "";

    private static void section(ForgeConfigSpec.Builder b, String key, String title, String comment) {
        b.comment(comment).push(key);
        curTitle = title;
    }

    private static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder b, String key, String label, String comment, boolean def) {
        ForgeConfigSpec.BooleanValue v = b.comment(comment).define(key, def);
        OPTS.add(new Opt(curTitle, key, label, comment, Opt.BOOL, 0, 1, def, v));
        return v;
    }

    private static ForgeConfigSpec.IntValue integer(ForgeConfigSpec.Builder b, String key, String label, String comment, int def, int min, int max) {
        ForgeConfigSpec.IntValue v = b.comment(comment).defineInRange(key, def, min, max);
        OPTS.add(new Opt(curTitle, key, label, comment, Opt.INT, min, max, def, v));
        return v;
    }

    private static ForgeConfigSpec.DoubleValue dbl(ForgeConfigSpec.Builder b, String key, String label, String comment, double def, double min, double max) {
        ForgeConfigSpec.DoubleValue v = b.comment(comment).defineInRange(key, def, min, max);
        OPTS.add(new Opt(curTitle, key, label, comment, Opt.DOUBLE, min, max, def, v));
        return v;
    }

    // --- Limites ---
    private static final ForgeConfigSpec.IntValue MAX_TIME_CFG;
    // --- Chrono Stasis arremess\u00e1vel (Shift) ---
    private static final ForgeConfigSpec.BooleanValue THROW_ENABLED;
    private static final ForgeConfigSpec.DoubleValue THROW_MANA_REQUIRED, THROW_MANA_COST, THROW_SPEED, THROW_FREEZE_SECONDS;
    private static final ForgeConfigSpec.IntValue THROW_TIME_REQUIRED, THROW_TIME_COST, THROW_EXPERIENCE, THROW_COOLDOWN_TICKS;
    // --- Time Steal arremess\u00e1vel (Shift) ---
    private static final ForgeConfigSpec.DoubleValue STEAL_MANA_REQUIRED, STEAL_MANA_COST, STEAL_DRAIN_HP, STEAL_TIME_PER_HP;
    private static final ForgeConfigSpec.IntValue STEAL_EXPERIENCE, STEAL_PUNCH_GAIN;
    // --- Chrono Stasis sustentado (Alt+Shift) ---
    private static final ForgeConfigSpec.BooleanValue SUSTAIN_ENABLED, SUSTAIN_ACCUM;
    private static final ForgeConfigSpec.IntValue SUSTAIN_MAX_TARGETS;
    private static final ForgeConfigSpec.DoubleValue SUSTAIN_MANA_PER_SECOND, SUSTAIN_WARN_SECONDS, SUSTAIN_DMG_MULT;
    // --- Grigora sustentado (Alt) ---
    private static final ForgeConfigSpec.BooleanValue GRIGORA_ENABLED, GRIGORA_SKIP_BOSSES;
    private static final ForgeConfigSpec.DoubleValue GRIGORA_RADIUS, GRIGORA_MANA;
    private static final ForgeConfigSpec.IntValue GRIGORA_MAX, GRIGORA_TIME, GRIGORA_COOLDOWN;
    // --- Zona de proj\u00e9teis (B) ---
    private static final ForgeConfigSpec.BooleanValue ZONE_ENABLED, ZONE_OWN_PASS, ZONE_ORBS;
    private static final ForgeConfigSpec.DoubleValue ZONE_RADIUS, ZONE_MANA_PER_SECOND, ZONE_MANA_TO_START, ZONE_CAPTURE_REACH;
    // --- Antever ataques ---
    private static final ForgeConfigSpec.BooleanValue PRECOG_ENABLED, PRECOG_REQUIRE_COMBAT, PRECOG_MOBS;
    private static final ForgeConfigSpec.DoubleValue PRECOG_RANGE;
    private static final ForgeConfigSpec.IntValue PRECOG_HORIZON;
    // --- Acelerar o tempo ---
    private static final ForgeConfigSpec.BooleanValue ACCEL_ENABLED, ACCEL_SLOW_ENEMIES, ACCEL_SLOW_PLAYERS;
    private static final ForgeConfigSpec.DoubleValue ACCEL_MANA, ACCEL_MANA_START, ACCEL_SLOW_RADIUS;
    private static final ForgeConfigSpec.IntValue ACCEL_MAX_SECONDS, ACCEL_COOLDOWN, ACCEL_SPEED, ACCEL_HASTE, ACCEL_SLOW_AMP;
    // --- Time Hop ---
    private static final ForgeConfigSpec.BooleanValue HOP_ENABLED, HOP_PULSE, HOP_PULSE_PLAYERS, HOP_PULSE_BOSSES, HOP_TRAIL;
    private static final ForgeConfigSpec.DoubleValue HOP_RADIUS;
    private static final ForgeConfigSpec.IntValue HOP_FALL, HOP_PROTECT, HOP_PULSE_TICKS, HOP_TRAIL_TICKS;
    // --- Swallowtail ---
    private static final ForgeConfigSpec.IntValue SWALLOW_CAPACITY, SWALLOW_TRANSFER;
    // --- Renascimento ---
    private static final ForgeConfigSpec.BooleanValue REBIRTH_ENABLED, REBIRTH_BLOCK_REGEN;
    private static final ForgeConfigSpec.IntValue REBIRTH_UNLOCK_TIME;
    // --- Visual / HUD ---
    private static final ForgeConfigSpec.BooleanValue HUD_TIME_GAIN, HIDE_PARTICLES;
    private static final ForgeConfigSpec.IntValue HUD_MIN_GAIN;
    private static final ForgeConfigSpec.DoubleValue SPHERE_SCALE_CFG;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        section(b, "limits", "Limites", "Limites");
        MAX_TIME_CFG = integer(b, "max_time", "Limite de Time (0 = sem limite)",
                "Limite maximo de Time. 0 = sem limite (padrao). Se definir, acima disso o addon corrige o valor.", 0, 0, 1000000);
        b.pop();

        section(b, "throw", "Arremesso (Shift + habilidade)", "Chrono Stasis arremessavel: Shift + Chrono Stasis");
        THROW_ENABLED = bool(b, "enabled", "Arremessos ligados", "Liga/desliga o arremesso (Chrono Stasis e Time Steal arremessaveis)", true);
        THROW_MANA_REQUIRED = dbl(b, "mana_required", "Mana minima", "Mana minima (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        THROW_MANA_COST = dbl(b, "mana_cost", "Mana gasta", "Mana gasta por arremesso", 15.0, 0.0, 1000.0);
        THROW_TIME_REQUIRED = integer(b, "time_required", "Time minimo", "Time minimo (precisa ter MAIS que isso)", 20, 0, 1000);
        THROW_TIME_COST = integer(b, "time_cost", "Time gasto", "Time gasto por arremesso", 20, 0, 1000);
        THROW_EXPERIENCE = integer(b, "experience", "Experiencia ganha", "Experiencia da habilidade ganha por arremesso", 15, 0, 10000);
        THROW_SPEED = dbl(b, "speed", "Velocidade da esfera", "Velocidade da esfera", 1.6, 0.2, 5.0);
        THROW_COOLDOWN_TICKS = integer(b, "cooldown_ticks", "Recarga (ticks)", "Recarga em ticks (20 ticks = 1 segundo), vale para os dois arremessos", 60, 0, 6000);
        THROW_FREEZE_SECONDS = dbl(b, "freeze_seconds", "Duracao do congelamento (s)", "Duracao do congelamento da esfera de Chrono Stasis (segundos)", 5.0, 0.5, 600.0);
        b.pop();

        section(b, "steal", "Time Steal arremessavel", "Time Steal arremessavel: Shift + Time Steal. Converte vida do alvo em Time.");
        STEAL_MANA_REQUIRED = dbl(b, "mana_required", "Mana minima", "Mana minima (precisa ter MAIS que isso)", 15.0, 0.0, 1000.0);
        STEAL_MANA_COST = dbl(b, "mana_cost", "Mana gasta", "Mana gasta por arremesso", 15.0, 0.0, 1000.0);
        STEAL_EXPERIENCE = integer(b, "experience", "Experiencia ganha", "Experiencia da habilidade ganha por arremesso", 15, 0, 10000);
        STEAL_DRAIN_HP = dbl(b, "drain_hp", "Vida drenada por acerto", "Vida maxima drenada por acerto (2 = 1 coracao)", 10.0, 0.5, 1000.0);
        STEAL_PUNCH_GAIN = integer(b, "punch_fixed_gain", "Time por soco do Time Steal normal (0 = original)", "Time ganho a cada soco do Time Steal normal (sem Shift). 0 = deixa o comportamento original do mod (+10 e so abaixo de 1000).", 20, 0, 100000);
        STEAL_TIME_PER_HP = dbl(b, "time_per_hp", "Time por ponto de vida", "Time ganho por ponto de vida drenado", 3.0, 0.0, 1000.0);
        b.pop();

        section(b, "sustained", "Chrono Stasis sustentado (Alt+Shift)", "Chrono Stasis sustentado: Alt + Shift + Chrono Stasis (a tecla Alt pode ser trocada nos Controles)");
        SUSTAIN_ENABLED = bool(b, "enabled", "Ligado", "Liga/desliga o Chrono Stasis sustentado", true);
        SUSTAIN_MAX_TARGETS = integer(b, "max_targets", "Alvos ao mesmo tempo", "Quantos alvos ao mesmo tempo", 3, 1, 20);
        SUSTAIN_MANA_PER_SECOND = dbl(b, "mana_per_second", "Mana por segundo (cada alvo)", "Mana por segundo PARA CADA alvo", 4.0, 0.0, 1000.0);
        SUSTAIN_WARN_SECONDS = dbl(b, "warn_seconds", "Aviso de tempo instavel (s)", "Aviso de 'tempo instavel' quando restarem tantos segundos de mana", 3.0, 0.0, 60.0);
        SUSTAIN_ACCUM = bool(b, "accumulate_damage", "Acumular dano", "true = o dano que o alvo preso recebe fica guardado e cai todo de uma vez ao solta-lo", true);
        SUSTAIN_DMG_MULT = dbl(b, "damage_multiplier", "Multiplicador do dano guardado", "Multiplicador do dano guardado", 1.0, 0.0, 100.0);
        b.pop();

        section(b, "grigora", "Grigora sustentado (Alt)", "Chrono Stasis Grigora sustentado: Alt + Chrono Stasis Grigora (prende todos em volta ate soltar)");
        GRIGORA_ENABLED = bool(b, "enabled", "Ligado", "Liga/desliga o Grigora sustentado", true);
        GRIGORA_RADIUS = dbl(b, "radius", "Raio (blocos)", "Raio em blocos", 16.0, 1.0, 64.0);
        GRIGORA_MAX = integer(b, "max_targets", "Maximo de alvos", "Maximo de alvos presos", 10, 1, 100);
        GRIGORA_MANA = dbl(b, "mana_per_second", "Mana por segundo (cada alvo)", "Mana por segundo PARA CADA alvo", 1.5, 0.0, 1000.0);
        GRIGORA_TIME = integer(b, "time_cost", "Time gasto ao ativar", "Time gasto ao ativar (precisa ter MAIS que isso)", 50, 0, 100000);
        GRIGORA_COOLDOWN = integer(b, "cooldown_ticks", "Recarga (ticks)", "Recarga em ticks", 200, 0, 100000);
        GRIGORA_SKIP_BOSSES = bool(b, "skip_bosses", "Poupar chefes", "true = Ender Dragon e Wither nao sao presos", true);
        b.pop();

        section(b, "zone", "Zona de projeteis (tecla B)", "Zona de projeteis: tecla B");
        ZONE_ENABLED = bool(b, "enabled", "Ligada", "Liga/desliga a zona de projeteis", true);
        ZONE_RADIUS = dbl(b, "radius", "Raio (blocos)", "Raio da zona em blocos", 7.0, 1.0, 64.0);
        ZONE_MANA_PER_SECOND = dbl(b, "mana_per_second", "Mana por segundo", "Mana por segundo enquanto ligada", 3.0, 0.0, 1000.0);
        ZONE_MANA_TO_START = dbl(b, "mana_to_start", "Mana minima para ligar", "Mana minima para ligar (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        ZONE_OWN_PASS = bool(b, "own_projectiles_pass", "Seus projeteis atravessam", "true = os projeteis do proprio dono atravessam a zona", true);
        ZONE_ORBS = bool(b, "projectile_orbs", "Orbe em cada projetil", "Desenhar um orbe do Chrono Stasis em cada projetil parado pela zona", true);
        ZONE_CAPTURE_REACH = dbl(b, "capture_reach", "Alcance de captura extra", "Alcance extra (blocos) para prever e parar projeteis muito rapidos", 6.0, 1.0, 32.0);
        b.pop();

        section(b, "precognition", "Antever ataques", "Antever ataques: avisa projeteis e mobs prestes a te atingir (usuarios de Tempo)");
        PRECOG_ENABLED = bool(b, "enabled", "Ligado", "Liga/desliga o aviso de ataques", true);
        PRECOG_REQUIRE_COMBAT = bool(b, "require_combat_bar", "So com a combat bar", "true = so com a combat bar ativa", true);
        PRECOG_RANGE = dbl(b, "range", "Alcance (blocos)", "Alcance de busca em blocos", 24.0, 4.0, 64.0);
        PRECOG_HORIZON = integer(b, "horizon_ticks", "Previsao (ticks)", "Quantos ticks a frente prever (20 = 1 segundo)", 30, 5, 200);
        PRECOG_MOBS = bool(b, "warn_mobs", "Avisar de mobs", "Avisar tambem de mobs prestes a atacar de perto", true);
        b.pop();

        section(b, "acceleration", "Acelerar o tempo (tecla V)", "Acelerar o tempo: tecla V. Voce fica mais rapido e ataca mais rapido; mobs em volta ficam lentos.");
        ACCEL_ENABLED = bool(b, "enabled", "Ligado", "Liga/desliga a aceleracao do tempo", true);
        ACCEL_MANA = dbl(b, "mana_per_second", "Mana por segundo", "Mana por segundo enquanto acelerado", 5.0, 0.0, 1000.0);
        ACCEL_MANA_START = dbl(b, "mana_to_start", "Mana minima para ligar", "Mana minima para ligar (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        ACCEL_MAX_SECONDS = integer(b, "max_seconds", "Duracao maxima (s, 0 = ate a mana acabar)", "Duracao maxima em segundos. 0 = ate a mana acabar.", 20, 0, 3600);
        ACCEL_COOLDOWN = integer(b, "cooldown_ticks", "Recarga apos terminar (ticks)", "Recarga depois que termina, em ticks (20 = 1 segundo)", 100, 0, 100000);
        ACCEL_SPEED = integer(b, "speed_level", "Nivel de velocidade (0 = I)", "Nivel do efeito Velocidade (0 = I, 1 = II, 2 = III...)", 1, 0, 9);
        ACCEL_HASTE = integer(b, "haste_level", "Nivel de ataque rapido (0 = I)", "Nivel do efeito Pressa, que acelera os ataques (0 = I, 1 = II...)", 1, 0, 9);
        ACCEL_SLOW_ENEMIES = bool(b, "slow_enemies", "Deixar mobs lentos", "true = mobs em volta ficam lentos", true);
        ACCEL_SLOW_RADIUS = dbl(b, "slow_radius", "Raio da lentidao (blocos)", "Raio da lentidao em blocos", 8.0, 1.0, 64.0);
        ACCEL_SLOW_AMP = integer(b, "slow_level", "Nivel da lentidao (0 = I)", "Nivel do efeito Lentidao (0 = I, 1 = II...)", 1, 0, 6);
        ACCEL_SLOW_PLAYERS = bool(b, "slow_players", "Deixar jogadores lentos (PvP)", "true = outros jogadores tambem ficam lentos", false);
        b.pop();

        section(b, "hop", "Time Hop (efeitos)", "Efeitos extras do Time Hop original");
        HOP_ENABLED = bool(b, "enabled", "Efeitos ligados", "true = aplica os efeitos abaixo quando voce usa o Time Hop", true);
        HOP_FALL = integer(b, "fall_immunity_ticks", "Sem dano de queda (ticks)", "Por quanto tempo apos o salto a queda nao causa dano (0 = desligado, 20 = 1 s)", 100, 0, 1200);
        HOP_PROTECT = integer(b, "protect_ticks", "Chegada protegida (ticks)", "Tempo sem dano e sem knockback ao chegar (0 = desligado)", 10, 0, 200);
        HOP_PULSE = bool(b, "pulse_enabled", "Pulso de tempo parado", "true = ao chegar, mobs e projeteis em volta ficam parados", true);
        HOP_RADIUS = dbl(b, "pulse_radius", "Raio do pulso (blocos)", "Raio do pulso de tempo parado", 6.0, 1.0, 32.0);
        HOP_PULSE_TICKS = integer(b, "pulse_ticks", "Duracao do pulso (ticks)", "Quanto tempo o pulso mantem tudo parado (20 = 1 s)", 20, 0, 200);
        HOP_PULSE_PLAYERS = bool(b, "pulse_players", "Pulso congela jogadores", "true = outros jogadores tambem param (PvP)", false);
        HOP_PULSE_BOSSES = bool(b, "pulse_bosses", "Pulso congela chefes", "true = Ender Dragon e Wither tambem param", false);
        HOP_TRAIL = bool(b, "trail_enabled", "Bolha fantasma", "true = deixa uma bolha de Chrono Stasis com a sua skin dentro, parada no ponto de partida", true);
        HOP_TRAIL_TICKS = integer(b, "trail_ticks", "Duracao da bolha fantasma (ticks)", "Quanto tempo a bolha fica no ponto de partida (20 = 1 s)", 40, 0, 400);
        b.pop();

        section(b, "swallowtail", "Swallowtail", "Swallowtail: artefato que armazena Time");
        SWALLOW_CAPACITY = integer(b, "capacity", "Capacidade de Time", "Capacidade de Time armazenado", 5000, 1, 10000000);
        SWALLOW_TRANSFER = integer(b, "transfer_per_use", "Time por clique", "Time que passa por clique", 100, 1, 100000);
        b.pop();

        section(b, "rebirth", "Renascimento", "Renascimento (grimorio de uma pagina apos morrer). So acontece se houver Time guardado na Swallowtail; ao renascer, o Time do jogador e o da Swallowtail zeram.");
        REBIRTH_ENABLED = bool(b, "enabled", "Ligado", "Liga/desliga o renascimento", true);
        REBIRTH_UNLOCK_TIME = integer(b, "unlock_time", "Time para desbloquear", "Time necessario para os poderes e o grimorio voltarem", 200, 0, 1000000);
        REBIRTH_BLOCK_REGEN = bool(b, "block_passive_regen", "Sem regeneracao passiva", "true = sem regeneracao passiva de Time ate desbloquear", true);
        b.pop();

        section(b, "visual", "Visual e HUD", "Visual e HUD");
        HUD_TIME_GAIN = bool(b, "show_time_gain", "Numeros romanos ao roubar Time", "Mostrar numeros romanos subindo quando o Time e roubado (parte cliente)", true);
        HUD_MIN_GAIN = integer(b, "time_gain_min", "Ganho minimo para mostrar", "Ganho minimo de Time num tick para mostrar", 5, 1, 1000);
        HIDE_PARTICLES = bool(b, "hide_stasis_particles", "Esconder particulas ciano", "Esconder as particulas ciano do Chrono Stasis/Grigora (precisa reabrir o jogo)", true);
        SPHERE_SCALE_CFG = dbl(b, "sphere_scale", "Tamanho das esferas", "Tamanho das esferas dos mobs congelados (multiplicador)", 0.95, 0.3, 3.0);
        b.pop();

        SPEC = b.build();
    }

    @SubscribeEvent
    public static void onLoad(ModConfig.Loading event) { if (event.getConfig().getSpec() == SPEC) apply(); }

    @SubscribeEvent
    public static void onReload(ModConfig.Reloading event) { if (event.getConfig().getSpec() == SPEC) apply(); }

    /** Copia os valores do arquivo para as vari\u00e1veis do addon (tamb\u00e9m chamado ao salvar pela tela). */
    public static void apply() {
        ChronoThrowHandler.ENABLED = THROW_ENABLED.get();
        ChronoThrowHandler.MANA_REQUIRED = THROW_MANA_REQUIRED.get().floatValue();
        ChronoThrowHandler.MANA_COST = THROW_MANA_COST.get().floatValue();
        ChronoThrowHandler.TIME_REQUIRED = THROW_TIME_REQUIRED.get();
        ChronoThrowHandler.TIME_COST = THROW_TIME_COST.get();
        ChronoThrowHandler.EXPERIENCE_GAIN = THROW_EXPERIENCE.get();
        ChronoThrowHandler.THROW_SPEED = THROW_SPEED.get().floatValue();
        ChronoThrowHandler.COOLDOWN_TICKS = THROW_COOLDOWN_TICKS.get();
        StasisOrbEntity.FREEZE_TICKS = (int) Math.round(THROW_FREEZE_SECONDS.get() * 20.0);

        ChronoThrowHandler.STEAL_MANA_REQUIRED = STEAL_MANA_REQUIRED.get().floatValue();
        ChronoThrowHandler.STEAL_MANA_COST = STEAL_MANA_COST.get().floatValue();
        ChronoThrowHandler.STEAL_EXPERIENCE = STEAL_EXPERIENCE.get();
        ChronoThrowHandler.STEAL_DRAIN_HP = STEAL_DRAIN_HP.get().floatValue();
        ChronoThrowHandler.STEAL_TIME_PER_HP = STEAL_TIME_PER_HP.get().floatValue();

        TimeStealFix.FIXED_GAIN = STEAL_PUNCH_GAIN.get();
        SustainedStasis.ENABLED = SUSTAIN_ENABLED.get();
        SustainedStasis.MAX_TARGETS = SUSTAIN_MAX_TARGETS.get();
        SustainedStasis.MANA_PER_SECOND = SUSTAIN_MANA_PER_SECOND.get().floatValue();
        SustainedStasis.WARN_SECONDS = SUSTAIN_WARN_SECONDS.get().floatValue();
        SustainedStasis.ACCUMULATE_DAMAGE = SUSTAIN_ACCUM.get();
        SustainedStasis.DAMAGE_MULTIPLIER = SUSTAIN_DMG_MULT.get().floatValue();
        SustainedStasis.GRIGORA_ENABLED = GRIGORA_ENABLED.get();
        SustainedStasis.GRIGORA_RADIUS = GRIGORA_RADIUS.get().floatValue();
        SustainedStasis.GRIGORA_MAX_TARGETS = GRIGORA_MAX.get();
        SustainedStasis.GRIGORA_MANA_PER_SECOND = GRIGORA_MANA.get().floatValue();
        SustainedStasis.GRIGORA_TIME_COST = GRIGORA_TIME.get();
        SustainedStasis.GRIGORA_COOLDOWN_TICKS = GRIGORA_COOLDOWN.get();
        SustainedStasis.GRIGORA_SKIP_BOSSES = GRIGORA_SKIP_BOSSES.get();

        ChronoZone.ENABLED = ZONE_ENABLED.get();
        ChronoZone.RADIUS = ZONE_RADIUS.get().floatValue();
        ChronoZone.MANA_PER_SECOND = ZONE_MANA_PER_SECOND.get().floatValue();
        ChronoZone.MANA_TO_START = ZONE_MANA_TO_START.get().floatValue();
        ChronoZone.OWN_PROJECTILES_PASS = ZONE_OWN_PASS.get();
        ChronoZone.PROJECTILE_ORBS = ZONE_ORBS.get();
        StasisField.CAPTURE_REACH = ZONE_CAPTURE_REACH.get();

        Precognition.ENABLED = PRECOG_ENABLED.get();
        Precognition.REQUIRE_COMBAT = PRECOG_REQUIRE_COMBAT.get();
        Precognition.RANGE = PRECOG_RANGE.get();
        Precognition.HORIZON_TICKS = PRECOG_HORIZON.get();
        Precognition.WARN_MOBS = PRECOG_MOBS.get();

        TimeAcceleration.ENABLED = ACCEL_ENABLED.get();
        TimeAcceleration.MANA_PER_SECOND = ACCEL_MANA.get().floatValue();
        TimeAcceleration.MANA_TO_START = ACCEL_MANA_START.get().floatValue();
        TimeAcceleration.MAX_SECONDS = ACCEL_MAX_SECONDS.get();
        TimeAcceleration.COOLDOWN_TICKS = ACCEL_COOLDOWN.get();
        TimeAcceleration.SPEED_AMPLIFIER = ACCEL_SPEED.get();
        TimeAcceleration.HASTE_AMPLIFIER = ACCEL_HASTE.get();
        TimeAcceleration.SLOW_ENEMIES = ACCEL_SLOW_ENEMIES.get();
        TimeAcceleration.SLOW_RADIUS = ACCEL_SLOW_RADIUS.get().floatValue();
        TimeAcceleration.SLOW_AMPLIFIER = ACCEL_SLOW_AMP.get();
        TimeAcceleration.SLOW_PLAYERS = ACCEL_SLOW_PLAYERS.get();

        TimeHopEffects.ENABLED = HOP_ENABLED.get();
        TimeHopEffects.FALL_IMMUNITY_TICKS = HOP_FALL.get();
        TimeHopEffects.PROTECT_TICKS = HOP_PROTECT.get();
        TimeHopEffects.PULSE_ENABLED = HOP_PULSE.get();
        TimeHopEffects.PULSE_RADIUS = HOP_RADIUS.get().floatValue();
        TimeHopEffects.PULSE_TICKS = HOP_PULSE_TICKS.get();
        TimeHopEffects.PULSE_PLAYERS = HOP_PULSE_PLAYERS.get();
        TimeHopEffects.PULSE_BOSSES = HOP_PULSE_BOSSES.get();
        TimeHopEffects.TRAIL_ENABLED = HOP_TRAIL.get();
        TimeHopEffects.TRAIL_TICKS = HOP_TRAIL_TICKS.get();
        SwallowtailItem.CAPACITY = SWALLOW_CAPACITY.get();
        SwallowtailItem.TRANSFER_PER_USE = SWALLOW_TRANSFER.get();

        RebirthHandler.ENABLED = REBIRTH_ENABLED.get();
        RebirthHandler.UNLOCK_TIME = REBIRTH_UNLOCK_TIME.get();
        RebirthHandler.BLOCK_PASSIVE_REGEN = REBIRTH_BLOCK_REGEN.get();

        TimeGainWatcher.MAX_TIME = MAX_TIME_CFG.get();
        ChronoThrowHandler.TIME_MAX = MAX_TIME_CFG.get();
        TimeGainWatcher.ENABLED = HUD_TIME_GAIN.get();
        TimeGainWatcher.MIN_GAIN = HUD_MIN_GAIN.get();
        ParticleFilter.HIDE_STASIS_PARTICLES = HIDE_PARTICLES.get();
        StasisField.SPHERE_SCALE = SPHERE_SCALE_CFG.get().floatValue();
    }
}
