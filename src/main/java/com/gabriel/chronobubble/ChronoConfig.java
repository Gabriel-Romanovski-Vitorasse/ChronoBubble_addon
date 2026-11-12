package com.gabriel.chronobubble;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuração, em dois arquivos:
 * <ul>
 *   <li><b>servidor</b> ({@code <mundo>/serverconfig/chrono_bubble-server.toml}): regras de jogo. O Forge manda uma
 *   cópia para cada cliente ao entrar, então o cliente desenha com os mesmos valores do servidor (ex.: raio da zona).
 *   Para valer em mundos novos, copie o arquivo para {@code defaultconfigs/}.</li>
 *   <li><b>cliente</b> ({@code config/chrono_bubble-client.toml}): só visual e HUD deste computador.</li>
 * </ul>
 * O código lê os valores direto daqui ({@code ChronoConfig.X.get()}); não há cópia em outras classes.
 * Antes de o arquivo carregar (ex.: no menu principal), {@code get()} devolve o valor padrão.
 * Os nomes e dicas da tela de configuração ficam nos arquivos de idioma ({@code config.chrono_bubble.*}).
 * <p>
 * ARQUIVO GERADO por {@code tools/config/genconfig.py} a partir de {@code tools/config/opts.py}: para mudar uma
 * opção, edite o opts.py e rode o gerador (não edite este arquivo à mão).
 */
public final class ChronoConfig {
    private ChronoConfig() {
    }

    /** Descrição de uma opção, usada pela tela de configuração. */
    public static final class Opt {
        public static final int BOOL = 0, INT = 1, DOUBLE = 2;
        public final String section, key;
        /** true = regra do servidor; false = opção visual deste computador. */
        public final boolean server;
        public final int kind;
        public final double min, max;
        public final Object def;
        public final ForgeConfigSpec.ConfigValue<?> value;

        Opt(String section, String key, boolean server, int kind, double min, double max, Object def,
            ForgeConfigSpec.ConfigValue<?> value) {
            this.section = section; this.key = key; this.server = server;
            this.kind = kind; this.min = min; this.max = max; this.def = def; this.value = value;
        }

        public String sectionKey() { return "config." + ChronoBubbleMod.MODID + "." + section; }
        public String labelKey() { return sectionKey() + "." + key; }
        public String tooltipKey() { return labelKey() + ".tooltip"; }
        public ForgeConfigSpec spec() { return server ? SERVER_SPEC : CLIENT_SPEC; }
    }

    private static final List<Opt> ALL = new ArrayList<>();
    /** Todas as opções, na ordem em que aparecem na tela. */
    public static final List<Opt> OPTS = Collections.unmodifiableList(ALL);
    private static String curSection = "";
    private static boolean curServer;

    private static void section(ForgeConfigSpec.Builder b, String key, boolean server, String comment) {
        b.comment(comment).push(key);
        curSection = key;
        curServer = server;
    }

    private static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder b, String key, String comment, boolean def) {
        ForgeConfigSpec.BooleanValue v = b.comment(comment).define(key, def);
        ALL.add(new Opt(curSection, key, curServer, Opt.BOOL, 0, 1, def, v));
        return v;
    }

    private static ForgeConfigSpec.IntValue integer(ForgeConfigSpec.Builder b, String key, String comment, int def, int min, int max) {
        ForgeConfigSpec.IntValue v = b.comment(comment).defineInRange(key, def, min, max);
        ALL.add(new Opt(curSection, key, curServer, Opt.INT, min, max, def, v));
        return v;
    }

    private static ForgeConfigSpec.DoubleValue dbl(ForgeConfigSpec.Builder b, String key, String comment, double def, double min, double max) {
        ForgeConfigSpec.DoubleValue v = b.comment(comment).defineInRange(key, def, min, max);
        ALL.add(new Opt(curSection, key, curServer, Opt.DOUBLE, min, max, def, v));
        return v;
    }

    /** Valor decimal como float (a maior parte do jogo usa float). */
    public static float f(ForgeConfigSpec.DoubleValue v) {
        return v.get().floatValue();
    }

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.IntValue MAX_TIME;
    public static final ForgeConfigSpec.BooleanValue THROW_ENABLED;
    public static final ForgeConfigSpec.DoubleValue THROW_MANA_REQUIRED;
    public static final ForgeConfigSpec.DoubleValue THROW_MANA_COST;
    public static final ForgeConfigSpec.IntValue THROW_TIME_REQUIRED;
    public static final ForgeConfigSpec.IntValue THROW_TIME_COST;
    public static final ForgeConfigSpec.IntValue THROW_EXPERIENCE;
    public static final ForgeConfigSpec.DoubleValue THROW_SPEED;
    public static final ForgeConfigSpec.IntValue THROW_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue THROW_FREEZE_SECONDS;
    public static final ForgeConfigSpec.DoubleValue STEAL_MANA_REQUIRED;
    public static final ForgeConfigSpec.DoubleValue STEAL_MANA_COST;
    public static final ForgeConfigSpec.IntValue STEAL_EXPERIENCE;
    public static final ForgeConfigSpec.DoubleValue STEAL_DRAIN_HP;
    public static final ForgeConfigSpec.IntValue STEAL_PUNCH_GAIN;
    public static final ForgeConfigSpec.DoubleValue STEAL_TIME_PER_HP;
    public static final ForgeConfigSpec.BooleanValue SUSTAIN_ENABLED;
    public static final ForgeConfigSpec.IntValue SUSTAIN_MAX_TARGETS;
    public static final ForgeConfigSpec.DoubleValue SUSTAIN_MANA_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue SUSTAIN_WARN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SUSTAIN_ACCUM;
    public static final ForgeConfigSpec.DoubleValue SUSTAIN_DMG_MULT;
    public static final ForgeConfigSpec.BooleanValue GRIGORA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue GRIGORA_RADIUS;
    public static final ForgeConfigSpec.IntValue GRIGORA_MAX_TARGETS;
    public static final ForgeConfigSpec.DoubleValue GRIGORA_MANA_PER_SECOND;
    public static final ForgeConfigSpec.IntValue GRIGORA_TIME_COST;
    public static final ForgeConfigSpec.IntValue GRIGORA_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue GRIGORA_SKIP_BOSSES;
    public static final ForgeConfigSpec.BooleanValue ZONE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ZONE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue ZONE_MANA_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue ZONE_MANA_TO_START;
    public static final ForgeConfigSpec.BooleanValue ZONE_OWN_PASS;
    public static final ForgeConfigSpec.BooleanValue ZONE_ORBS;
    public static final ForgeConfigSpec.DoubleValue ZONE_CAPTURE_REACH;
    public static final ForgeConfigSpec.BooleanValue PRECOG_ENABLED;
    public static final ForgeConfigSpec.BooleanValue PRECOG_REQUIRE_COMBAT;
    public static final ForgeConfigSpec.DoubleValue PRECOG_RANGE;
    public static final ForgeConfigSpec.IntValue PRECOG_HORIZON;
    public static final ForgeConfigSpec.BooleanValue PRECOG_MOBS;
    public static final ForgeConfigSpec.BooleanValue PRECOG_REQUIRE_MAGIC_ZONE;
    public static final ForgeConfigSpec.BooleanValue ACCEL_ENABLED;
    public static final ForgeConfigSpec.DoubleValue ACCEL_MANA_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue ACCEL_MANA_TO_START;
    public static final ForgeConfigSpec.IntValue ACCEL_MAX_SECONDS;
    public static final ForgeConfigSpec.IntValue ACCEL_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue ACCEL_SPEED_LEVEL;
    public static final ForgeConfigSpec.IntValue ACCEL_HASTE_LEVEL;
    public static final ForgeConfigSpec.BooleanValue ACCEL_SLOW_ENEMIES;
    public static final ForgeConfigSpec.DoubleValue ACCEL_SLOW_RADIUS;
    public static final ForgeConfigSpec.IntValue ACCEL_SLOW_LEVEL;
    public static final ForgeConfigSpec.BooleanValue ACCEL_SLOW_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue HOP_ENABLED;
    public static final ForgeConfigSpec.IntValue HOP_FALL_TICKS;
    public static final ForgeConfigSpec.IntValue HOP_PROTECT_TICKS;
    public static final ForgeConfigSpec.BooleanValue HOP_PULSE;
    public static final ForgeConfigSpec.DoubleValue HOP_PULSE_RADIUS;
    public static final ForgeConfigSpec.IntValue HOP_PULSE_TICKS;
    public static final ForgeConfigSpec.BooleanValue HOP_PULSE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue HOP_PULSE_BOSSES;
    public static final ForgeConfigSpec.BooleanValue HOP_TRAIL;
    public static final ForgeConfigSpec.IntValue HOP_TRAIL_TICKS;
    public static final ForgeConfigSpec.BooleanValue REHOP_ENABLED;
    public static final ForgeConfigSpec.DoubleValue REHOP_MANA_COST;
    public static final ForgeConfigSpec.IntValue REHOP_TIME_COST;
    public static final ForgeConfigSpec.DoubleValue REHOP_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue REHOP_DISTANCE_SNEAK;
    public static final ForgeConfigSpec.IntValue REHOP_DASH_TICKS;
    public static final ForgeConfigSpec.IntValue REHOP_LINGER_TICKS;
    public static final ForgeConfigSpec.IntValue REHOP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue REHOP_FREEZE_ENEMIES;
    public static final ForgeConfigSpec.BooleanValue REHOP_FREEZE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue REHOP_SKIP_BOSSES;
    public static final ForgeConfigSpec.BooleanValue REST_ENABLED;
    public static final ForgeConfigSpec.DoubleValue REST_RADIUS;
    public static final ForgeConfigSpec.IntValue REST_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue REST_MAX_BLOCKS;
    public static final ForgeConfigSpec.IntValue REST_TIME_COST;
    public static final ForgeConfigSpec.IntValue REST_TIME_PER_BLOCK;
    public static final ForgeConfigSpec.DoubleValue REST_MANA_COST;
    public static final ForgeConfigSpec.IntValue REST_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue REST_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue REST_REQUIRE_COMBAT;
    public static final ForgeConfigSpec.IntValue SWALLOW_CAPACITY;
    public static final ForgeConfigSpec.IntValue SWALLOW_TRANSFER;
    public static final ForgeConfigSpec.BooleanValue REBIRTH_ENABLED;
    public static final ForgeConfigSpec.IntValue REBIRTH_UNLOCK_TIME;
    public static final ForgeConfigSpec.BooleanValue REBIRTH_BLOCK_REGEN;
    public static final ForgeConfigSpec.BooleanValue REBIRTH_RESSURECTION_FIRST;
    public static final ForgeConfigSpec.BooleanValue HUD_TIME_GAIN;
    public static final ForgeConfigSpec.IntValue HUD_MIN_GAIN;
    public static final ForgeConfigSpec.BooleanValue HIDE_PARTICLES;
    public static final ForgeConfigSpec.DoubleValue SPHERE_SCALE;
    public static final ForgeConfigSpec.BooleanValue GRIMOIRE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue GRIMOIRE_SIDE;
    public static final ForgeConfigSpec.DoubleValue GRIMOIRE_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue GRIMOIRE_FRONT;
    public static final ForgeConfigSpec.DoubleValue GRIMOIRE_SCALE;
    public static final ForgeConfigSpec.BooleanValue SECOND_RING;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();

        section(s, "limits", true, "Limites");
        MAX_TIME = integer(s, "max_time", "Limite m\u00e1ximo de Time. 0 = sem limite. Acima disso o addon corrige o valor.", 0, 0, 1000000);
        s.pop();

        section(s, "throw", true, "Chrono Stasis arremess\u00e1vel: Shift + Chrono Stasis");
        THROW_ENABLED = bool(s, "enabled", "Liga/desliga o arremesso (Chrono Stasis e Time Steal arremess\u00e1veis)", true);
        THROW_MANA_REQUIRED = dbl(s, "mana_required", "Mana m\u00ednima (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        THROW_MANA_COST = dbl(s, "mana_cost", "Mana gasta por arremesso", 15.0, 0.0, 1000.0);
        THROW_TIME_REQUIRED = integer(s, "time_required", "Time m\u00ednimo (precisa ter MAIS que isso)", 20, 0, 1000);
        THROW_TIME_COST = integer(s, "time_cost", "Time gasto por arremesso", 20, 0, 1000);
        THROW_EXPERIENCE = integer(s, "experience", "Experi\u00eancia da habilidade ganha por arremesso", 15, 0, 10000);
        THROW_SPEED = dbl(s, "speed", "Velocidade da esfera", 1.6, 0.2, 5.0);
        THROW_COOLDOWN_TICKS = integer(s, "cooldown_ticks", "Recarga em ticks (20 ticks = 1 segundo), vale para os dois arremessos", 60, 0, 6000);
        THROW_FREEZE_SECONDS = dbl(s, "freeze_seconds", "Dura\u00e7\u00e3o do congelamento da esfera de Chrono Stasis (segundos)", 5.0, 0.5, 600.0);
        s.pop();

        section(s, "steal", true, "Time Steal arremess\u00e1vel: Shift + Time Steal. Converte vida do alvo em Time.");
        STEAL_MANA_REQUIRED = dbl(s, "mana_required", "Mana m\u00ednima (precisa ter MAIS que isso)", 15.0, 0.0, 1000.0);
        STEAL_MANA_COST = dbl(s, "mana_cost", "Mana gasta por arremesso", 15.0, 0.0, 1000.0);
        STEAL_EXPERIENCE = integer(s, "experience", "Experi\u00eancia da habilidade ganha por arremesso", 15, 0, 10000);
        STEAL_DRAIN_HP = dbl(s, "drain_hp", "Vida m\u00e1xima drenada por acerto (2 = 1 cora\u00e7\u00e3o)", 10.0, 0.5, 1000.0);
        STEAL_PUNCH_GAIN = integer(s, "punch_fixed_gain", "Time ganho a cada soco do Time Steal normal (sem Shift). 0 = deixa o comportamento original do mod (+10 e s\u00f3 abaixo de 1000).", 20, 0, 100000);
        STEAL_TIME_PER_HP = dbl(s, "time_per_hp", "Time ganho por ponto de vida drenado", 3.0, 0.0, 1000.0);
        s.pop();

        section(s, "sustained", true, "Chrono Stasis sustentado: Alt + Shift + Chrono Stasis (a tecla Alt pode ser trocada nos Controles)");
        SUSTAIN_ENABLED = bool(s, "enabled", "Liga/desliga o Chrono Stasis sustentado", true);
        SUSTAIN_MAX_TARGETS = integer(s, "max_targets", "Quantos alvos ao mesmo tempo", 3, 1, 20);
        SUSTAIN_MANA_PER_SECOND = dbl(s, "mana_per_second", "Mana por segundo PARA CADA alvo", 4.0, 0.0, 1000.0);
        SUSTAIN_WARN_SECONDS = dbl(s, "warn_seconds", "Aviso de 'tempo inst\u00e1vel' quando restarem tantos segundos de mana", 3.0, 0.0, 60.0);
        SUSTAIN_ACCUM = bool(s, "accumulate_damage", "true = o dano que o alvo preso recebe fica guardado e cai todo de uma vez ao solt\u00e1-lo", true);
        SUSTAIN_DMG_MULT = dbl(s, "damage_multiplier", "Multiplicador do dano guardado", 1.0, 0.0, 100.0);
        s.pop();

        section(s, "grigora", true, "Chrono Stasis Grigora sustentado: Alt + Chrono Stasis Grigora (prende todos em volta at\u00e9 soltar)");
        GRIGORA_ENABLED = bool(s, "enabled", "Liga/desliga o Grigora sustentado", true);
        GRIGORA_RADIUS = dbl(s, "radius", "Raio em blocos", 16.0, 1.0, 64.0);
        GRIGORA_MAX_TARGETS = integer(s, "max_targets", "M\u00e1ximo de alvos presos", 10, 1, 100);
        GRIGORA_MANA_PER_SECOND = dbl(s, "mana_per_second", "Mana por segundo PARA CADA alvo", 1.5, 0.0, 1000.0);
        GRIGORA_TIME_COST = integer(s, "time_cost", "Time gasto ao ativar (precisa ter MAIS que isso)", 50, 0, 100000);
        GRIGORA_COOLDOWN_TICKS = integer(s, "cooldown_ticks", "Recarga em ticks", 200, 0, 100000);
        GRIGORA_SKIP_BOSSES = bool(s, "skip_bosses", "true = Ender Dragon e Wither n\u00e3o s\u00e3o presos", true);
        s.pop();

        section(s, "zone", true, "Zona de proj\u00e9teis: tecla B");
        ZONE_ENABLED = bool(s, "enabled", "Liga/desliga a zona de proj\u00e9teis", true);
        ZONE_RADIUS = dbl(s, "radius", "Raio da zona em blocos", 7.0, 1.0, 64.0);
        ZONE_MANA_PER_SECOND = dbl(s, "mana_per_second", "Mana por segundo enquanto ligada", 3.0, 0.0, 1000.0);
        ZONE_MANA_TO_START = dbl(s, "mana_to_start", "Mana m\u00ednima para ligar (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        ZONE_OWN_PASS = bool(s, "own_projectiles_pass", "true = os proj\u00e9teis do pr\u00f3prio dono atravessam a zona", true);
        ZONE_ORBS = bool(s, "projectile_orbs", "Desenhar um orbe do Chrono Stasis em cada proj\u00e9til parado pela zona", true);
        ZONE_CAPTURE_REACH = dbl(s, "capture_reach", "Alcance extra (blocos) para prever e parar proj\u00e9teis muito r\u00e1pidos", 6.0, 1.0, 32.0);
        s.pop();

        section(s, "precognition", true, "Antever ataques: avisa proj\u00e9teis e mobs prestes a te atingir (usu\u00e1rios de Tempo)");
        PRECOG_ENABLED = bool(s, "enabled", "Liga/desliga o aviso de ataques", true);
        PRECOG_REQUIRE_COMBAT = bool(s, "require_combat_bar", "true = s\u00f3 com a combat bar ativa", true);
        PRECOG_RANGE = dbl(s, "range", "Alcance de busca em blocos", 24.0, 4.0, 64.0);
        PRECOG_HORIZON = integer(s, "horizon_ticks", "Quantos ticks \u00e0 frente prever (20 = 1 segundo)", 30, 5, 200);
        PRECOG_MOBS = bool(s, "warn_mobs", "Avisar tamb\u00e9m de mobs prestes a atacar de perto", true);
        PRECOG_REQUIRE_MAGIC_ZONE = bool(s, "require_time_magic_zone", "true = s\u00f3 funciona com a passiva Time Magic Zone do Block Clover desbloqueada e ativa (\u00e9 ela que 'sente ataques futuros')", true);
        s.pop();

        section(s, "acceleration", true, "Acelerar o tempo: tecla V. Voc\u00ea fica mais r\u00e1pido e ataca mais r\u00e1pido; mobs em volta ficam lentos.");
        ACCEL_ENABLED = bool(s, "enabled", "Liga/desliga a acelera\u00e7\u00e3o do tempo", true);
        ACCEL_MANA_PER_SECOND = dbl(s, "mana_per_second", "Mana por segundo enquanto acelerado", 5.0, 0.0, 1000.0);
        ACCEL_MANA_TO_START = dbl(s, "mana_to_start", "Mana m\u00ednima para ligar (precisa ter MAIS que isso)", 20.0, 0.0, 1000.0);
        ACCEL_MAX_SECONDS = integer(s, "max_seconds", "Dura\u00e7\u00e3o m\u00e1xima em segundos. 0 = at\u00e9 a mana acabar.", 20, 0, 3600);
        ACCEL_COOLDOWN_TICKS = integer(s, "cooldown_ticks", "Recarga depois que termina, em ticks (20 = 1 segundo)", 100, 0, 100000);
        ACCEL_SPEED_LEVEL = integer(s, "speed_level", "N\u00edvel do efeito Velocidade (0 = I, 1 = II, 2 = III...)", 1, 0, 9);
        ACCEL_HASTE_LEVEL = integer(s, "haste_level", "N\u00edvel do efeito Pressa, que acelera os ataques (0 = I, 1 = II...)", 1, 0, 9);
        ACCEL_SLOW_ENEMIES = bool(s, "slow_enemies", "true = mobs em volta ficam lentos", true);
        ACCEL_SLOW_RADIUS = dbl(s, "slow_radius", "Raio da lentid\u00e3o em blocos", 8.0, 1.0, 64.0);
        ACCEL_SLOW_LEVEL = integer(s, "slow_level", "N\u00edvel do efeito Lentid\u00e3o (0 = I, 1 = II...)", 1, 0, 6);
        ACCEL_SLOW_PLAYERS = bool(s, "slow_players", "true = outros jogadores tamb\u00e9m ficam lentos", false);
        s.pop();

        section(s, "hop", true, "Efeitos extras do Time Hop original (n\u00e3o valem com o Time Hop reformulado ligado)");
        HOP_ENABLED = bool(s, "enabled", "true = aplica os efeitos abaixo quando voc\u00ea usa o Time Hop", true);
        HOP_FALL_TICKS = integer(s, "fall_immunity_ticks", "Por quanto tempo ap\u00f3s o salto a queda n\u00e3o causa dano (0 = desligado, 20 = 1 s)", 100, 0, 1200);
        HOP_PROTECT_TICKS = integer(s, "protect_ticks", "Tempo sem dano e sem knockback ao chegar (0 = desligado)", 10, 0, 200);
        HOP_PULSE = bool(s, "pulse_enabled", "true = ao chegar, mobs e proj\u00e9teis em volta ficam parados", true);
        HOP_PULSE_RADIUS = dbl(s, "pulse_radius", "Raio do pulso de tempo parado", 6.0, 1.0, 32.0);
        HOP_PULSE_TICKS = integer(s, "pulse_ticks", "Quanto tempo o pulso mant\u00e9m tudo parado (20 = 1 s)", 20, 0, 200);
        HOP_PULSE_PLAYERS = bool(s, "pulse_players", "true = outros jogadores tamb\u00e9m param (PvP)", false);
        HOP_PULSE_BOSSES = bool(s, "pulse_bosses", "true = Ender Dragon e Wither tamb\u00e9m param", false);
        HOP_TRAIL = bool(s, "trail_enabled", "true = deixa uma bolha de Chrono Stasis com a sua skin dentro, parada no ponto de partida", true);
        HOP_TRAIL_TICKS = integer(s, "trail_ticks", "Quanto tempo a bolha fica no ponto de partida (20 = 1 s)", 40, 0, 400);
        s.pop();

        section(s, "hop_rework", true, "Time Hop reformulado: substitui o teletransporte instant\u00e2neo do Block Clover por um deslize dentro de uma esfera de tempo parado");
        REHOP_ENABLED = bool(s, "enabled", "true = o Time Hop original \u00e9 cancelado e trocado pelo deslize (os 'efeitos' acima deixam de valer)", false);
        REHOP_MANA_COST = dbl(s, "mana_cost", "Mana gasta por salto", 20.0, 0.0, 1000.0);
        REHOP_TIME_COST = integer(s, "time_cost", "Time gasto por salto (precisa ter MAIS que isso)", 20, 0, 100000);
        REHOP_DISTANCE = dbl(s, "distance", "Dist\u00e2ncia m\u00e1xima do salto", 8.0, 1.0, 64.0);
        REHOP_DISTANCE_SNEAK = dbl(s, "distance_sneak", "Dist\u00e2ncia m\u00e1xima do salto agachado", 20.0, 1.0, 64.0);
        REHOP_DASH_TICKS = integer(s, "dash_ticks", "Quantos ticks dura o deslize at\u00e9 o destino", 6, 1, 40);
        REHOP_LINGER_TICKS = integer(s, "linger_ticks", "Quanto tempo o tempo continua parado depois que voc\u00ea chega", 14, 0, 200);
        REHOP_COOLDOWN_TICKS = integer(s, "cooldown_ticks", "Recarga em ticks", 40, 0, 100000);
        REHOP_FREEZE_ENEMIES = bool(s, "freeze_enemies", "true = mobs perto do caminho ficam parados durante o salto", true);
        REHOP_FREEZE_PLAYERS = bool(s, "freeze_players", "true = outros jogadores tamb\u00e9m param", false);
        REHOP_SKIP_BOSSES = bool(s, "skip_bosses", "true = Ender Dragon e Wither n\u00e3o param", true);
        s.pop();

        section(s, "restoration", true, "Restaura\u00e7\u00e3o do tempo: Shift + Time Healing. Faz voltar os blocos quebrados ou explodidos h\u00e1 pouco, em volta de voc\u00ea, do mais recente para o mais antigo. Para n\u00e3o duplicar itens, um bloco s\u00f3 volta se os itens que ele derrubou ainda estiverem no ch\u00e3o (eles somem quando o bloco volta).");
        REST_ENABLED = bool(s, "enabled", "Liga/desliga a restaura\u00e7\u00e3o do tempo", true);
        REST_RADIUS = dbl(s, "radius", "Raio em volta do jogador; uma explos\u00e3o que encosta no raio volta inteira", 8.0, 1.0, 32.0);
        REST_WINDOW_SECONDS = integer(s, "window_seconds", "S\u00f3 blocos destru\u00eddos nos \u00faltimos tantos segundos podem voltar", 60, 5, 600);
        REST_MAX_BLOCKS = integer(s, "max_blocks", "M\u00e1ximo de blocos restaurados de uma vez", 256, 1, 4096);
        REST_TIME_COST = integer(s, "time_cost", "Time gasto a cada uso (precisa ter MAIS que isso)", 30, 0, 100000);
        REST_TIME_PER_BLOCK = integer(s, "time_per_block", "Time extra gasto por bloco restaurado", 2, 0, 1000);
        REST_MANA_COST = dbl(s, "mana_cost", "Mana gasta a cada uso (precisa ter MAIS que isso)", 25.0, 0.0, 1000.0);
        REST_COOLDOWN_TICKS = integer(s, "cooldown_ticks", "Recarga em ticks (20 = 1 segundo)", 600, 0, 100000);
        REST_BLOCKS_PER_TICK = integer(s, "blocks_per_tick", "Quantos blocos voltam por tick; menos = rebobinar mais lento", 6, 1, 64);
        REST_REQUIRE_COMBAT = bool(s, "require_combat_bar", "true = s\u00f3 funciona com a combat bar ativa", true);
        s.pop();

        section(s, "swallowtail", true, "Swallowtail: artefato que armazena Time");
        SWALLOW_CAPACITY = integer(s, "capacity", "Capacidade de Time armazenado", 5000, 1, 10000000);
        SWALLOW_TRANSFER = integer(s, "transfer_per_use", "Time que passa por clique", 100, 1, 100000);
        s.pop();

        section(s, "rebirth", true, "Renascimento (grim\u00f3rio de uma p\u00e1gina ap\u00f3s morrer). S\u00f3 acontece se houver Time guardado na Swallowtail; ao renascer, o Time do jogador e o da Swallowtail zeram.");
        REBIRTH_ENABLED = bool(s, "enabled", "Liga/desliga o renascimento", true);
        REBIRTH_UNLOCK_TIME = integer(s, "unlock_time", "Time necess\u00e1rio para os poderes e o grim\u00f3rio voltarem", 200, 0, 1000000);
        REBIRTH_BLOCK_REGEN = bool(s, "block_passive_regen", "true = sem regenera\u00e7\u00e3o passiva de Time at\u00e9 desbloquear", true);
        REBIRTH_RESSURECTION_FIRST = bool(s, "time_ressurection_first", "true = ao receber um golpe fatal, a passiva Time Ressurection age primeiro se estiver pronta (carga cheia e mais de 300 de Time; consome todo o Time) e a Swallowtail fica intacta. Se n\u00e3o estiver pronta, vale o renascimento pela Swallowtail.", true);
        s.pop();
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();

        section(c, "visual", false, "Visual e HUD (s\u00f3 neste computador)");
        HUD_TIME_GAIN = bool(c, "show_time_gain", "Mostrar n\u00fameros romanos subindo quando o Time \u00e9 roubado", true);
        HUD_MIN_GAIN = integer(c, "time_gain_min", "Ganho m\u00ednimo de Time num tick para mostrar", 5, 1, 1000);
        HIDE_PARTICLES = bool(c, "hide_stasis_particles", "Esconder as part\u00edculas ciano do Chrono Stasis/Grigora (precisa reabrir o jogo)", true);
        SPHERE_SCALE = dbl(c, "sphere_scale", "Tamanho das esferas dos mobs congelados (multiplicador)", 0.95, 0.3, 3.0);
        GRIMOIRE_ENABLED = bool(c, "grimoire_enabled", "true = desenha o grim\u00f3rio de p\u00e1ginas em espiral ao lado de quem est\u00e1 com o grim\u00f3rio de Tempo em combate", true);
        GRIMOIRE_SIDE = dbl(c, "grimoire_side", "Deslocamento lateral em blocos. Negativo = lado direito do jogador, positivo = lado esquerdo, 0 = no meio", -0.85, -3.0, 3.0);
        GRIMOIRE_HEIGHT = dbl(c, "grimoire_height", "Altura em rela\u00e7\u00e3o aos p\u00e9s do jogador, em blocos", 1.15, 0.0, 4.0);
        GRIMOIRE_FRONT = dbl(c, "grimoire_front", "Deslocamento em blocos. Positivo = \u00e0 frente do jogador, negativo = atr\u00e1s", 0.15, -3.0, 3.0);
        GRIMOIRE_SCALE = dbl(c, "grimoire_scale", "Tamanho do grim\u00f3rio (multiplicador; 1 = normal)", 1.0, 0.2, 4.0);
        SECOND_RING = bool(c, "second_ring", "true = desenha um segundo anel, mais fino, inclinado para o outro lado (o anime usa um s\u00f3)", false);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    /** Registra os dois arquivos (chamado no construtor do mod). */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC, ChronoBubbleMod.MODID + "-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC, ChronoBubbleMod.MODID + "-client.toml");
    }
}
