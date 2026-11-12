package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.potion.Effect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Acessa o Block Clover por reflex\u00e3o (sem depender do .jar para compilar).
 * Se algo mudar nas classes do mod, as chamadas falham em sil\u00eancio e o recurso correspondente n\u00e3o faz nada.
 */
public class BlockCloverBridge {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String MOD_ID = "block_clover";
    public static final String KEY_STASIS = "stasis";
    public static final String KEY_GRIGORA = "grigora";

    // ---- Nomes do Block Clover usados pelo addon (todos aqui, para achar f\u00e1cil quando o mod mudar) ----
    /** Efeito de po\u00e7\u00e3o do Chrono Stasis (usado tamb\u00e9m pelo Grigora). */
    public static final ResourceLocation STASIS_EFFECT_ID = new ResourceLocation(MOD_ID, "chrono_stasis");
    /** Tipo de part\u00edcula das magias de tempo. */
    public static final ResourceLocation TIME_PARTICLE_ID = new ResourceLocation(MOD_ID, "time");
    private static final String PKG = "com.yuanno.block_clover.";
    public static final String CLS_STASIS = PKG + "spells.time.ChronoStasisAbility";
    public static final String CLS_GRIGORA = PKG + "spells.time.ChronoStasisGrigoraAbility";
    public static final String CLS_STEAL = PKG + "spells.time.TimeStealAbility";
    public static final String CLS_HOP = PKG + "spells.time.TimeHopAbility";
    /** Passiva "Time Magic Zone": sente ataques futuros. */
    public static final String CLS_MAGIC_ZONE = PKG + "spells.time.TimeMagicManaZoneAbility";
    /** Passiva "Time Ressurection": com a carga cheia, consome todo o Time e cura o jogador quase morto. */
    public static final String CLS_RESSURECTION = PKG + "spells.time.TimeRessurectionAbility";
    /** Regras da Time Ressurection no Block Clover 0.5 (copiadas do mod: carga m\u00ednima e Time m\u00ednimo). */
    public static final double RESSURECTION_FULL_STRAIN = 3600.0D;
    public static final int RESSURECTION_MIN_TIME = 300;
    /**
     * Evento de uso de habilidade. O Block Clover 0.5 dispara de dois jeitos (conferido no bytecode):
     * <ul>
     *   <li>habilidades comuns (Time Hop, Grigora): {@code AbilityUseEvent$Pre} antes de agir e
     *   {@code AbilityUseEvent$Post} depois;</li>
     *   <li>habilidades cont\u00ednuas/de soco (Chrono Stasis, Time Steal): o {@code AbilityUseEvent} base, uma vez.</li>
     * </ul>
     * Por isso o addon escuta a classe base (que recebe os tr\u00eas) e ignora o Post, que chega depois de a habilidade
     * agir (no Time Hop, com o jogador j\u00e1 teleportado).
     */
    public static final String CLS_ABILITY_USE_EVENT = PKG + "api.ability.AbilityUseEvent";
    /** Sufixo do evento disparado DEPOIS de a habilidade agir (ver acima). */
    public static final String ABILITY_USE_POST_SUFFIX = "$Post";

    private static Effect stasisEffect;

    /** O efeito Chrono Stasis, ou null se o Block Clover n\u00e3o estiver carregado. */
    public static Effect stasisEffect() {
        if (stasisEffect == null) stasisEffect = ForgeRegistries.POTIONS.getValue(STASIS_EFFECT_ID);
        return stasisEffect;
    }

    /** \u00c9 o efeito Chrono Stasis? */
    public static boolean isStasis(Effect effect) {
        return effect != null && STASIS_EFFECT_ID.equals(effect.getRegistryName());
    }

    private static boolean tried;
    private static boolean statsOk;
    private static boolean abilitiesOk;
    private static boolean syncOk;

    private static Method statsGet, getAttribute, getSecondAttribute, hasGrimoire, inCombat, getTime, setTime;
    private static Method getMana, alterMana, alterTime, alterExperience;
    private static Method abilityGet, hasUnlocked, remUnlocked, remEquipped, addUnlocked;
    private static Method sendTo;
    private static Method getUnlockedFiltered, passiveIsPaused;
    private static Class<?> passiveClass, magicZoneClass, ressurectionClass;
    private static Method ressurectionStrain, ressurectionReset;
    private static Method getEquipped;
    private static Object categoryAll;
    private static Class<?> stealClass;
    private static Method stealIsContinuous;
    private static Constructor<?> statsPacket, abilityPacket;
    private static Object coreStasis, coreGrigora;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> capStats = Class.forName(PKG + "data.entity.EntityStatsCapability");
            Class<?> stats = Class.forName(PKG + "data.entity.IEntityStats");
            statsGet = capStats.getMethod("get", LivingEntity.class);
            getAttribute = stats.getMethod("getAttribute");
            getSecondAttribute = stats.getMethod("getSecondAttribute");
            hasGrimoire = stats.getMethod("hasGrimoire");
            inCombat = stats.getMethod("isInCombatMode");
            getTime = stats.getMethod("getTime");
            setTime = stats.getMethod("setTime", int.class);
            getMana = stats.getMethod("getMana");
            alterMana = stats.getMethod("alterMana", float.class);
            alterTime = stats.getMethod("alterTime", int.class);
            alterExperience = stats.getMethod("alterExperience", int.class);
            statsOk = true;

            try {
                Class<?> capAbility = Class.forName(PKG + "data.ability.AbilityDataCapability");
                Class<?> data = Class.forName(PKG + "data.ability.IAbilityData");
                Class<?> core = Class.forName(PKG + "api.ability.AbilityCore");
                abilityGet = capAbility.getMethod("get", LivingEntity.class);
                hasUnlocked = data.getMethod("hasUnlockedAbility", core);
                remUnlocked = data.getMethod("removeUnlockedAbility", core);
                remEquipped = data.getMethod("removeEquippedAbility", core);
                addUnlocked = data.getMethod("addUnlockedAbility", PlayerEntity.class, core);
                coreStasis = Class.forName(CLS_STASIS).getField("INSTANCE").get(null);
                coreGrigora = Class.forName(CLS_GRIGORA).getField("INSTANCE").get(null);
                abilitiesOk = true;
                try {
                    Class<?> catClass = Class.forName(PKG + "api.ability.AbilityCategories$AbilityCategory");
                    categoryAll = catClass.getField("ALL").get(null);
                    getEquipped = data.getMethod("getEquippedAbilities", catClass);
                    stealClass = Class.forName(CLS_STEAL);
                    stealIsContinuous = stealClass.getMethod("isContinuous");
                } catch (Throwable ignored) {
                    // sem o Time Steal: o ganho fixo do soco simplesmente n\u00e3o age
                }
                try {
                    getUnlockedFiltered = data.getMethod("getUnlockedAbilities", java.util.function.Predicate.class);
                    passiveClass = Class.forName(PKG + "api.ability.sorts.PassiveAbility");
                    passiveIsPaused = passiveClass.getMethod("isPaused");
                    magicZoneClass = Class.forName(CLS_MAGIC_ZONE);
                    ressurectionClass = Class.forName(CLS_RESSURECTION);
                    ressurectionStrain = ressurectionClass.getMethod("getStrain");
                    ressurectionReset = ressurectionClass.getMethod("resetReviveTime", PlayerEntity.class, boolean.class);
                } catch (Throwable ignored) {
                    // sem as passivas: "antever" n\u00e3o exige a Time Magic Zone e a Time Ressurection n\u00e3o salva de golpes fatais
                    passiveClass = null;
                }

                Class<?> ph = Class.forName(PKG + "networking.PacketHandler");
                sendTo = ph.getMethod("sendTo", Object.class, PlayerEntity.class);
                statsPacket = Class.forName(PKG + "networking.server.SSyncEntityStatsPacket")
                        .getConstructor(int.class, stats);
                abilityPacket = Class.forName(PKG + "networking.server.SSyncAbilityDataPacket")
                        .getConstructor(int.class, data);
                syncOk = true;
            } catch (Throwable ignored) {
                // habilidades/sincroniza\u00e7\u00e3o indispon\u00edveis: o resto continua funcionando
            }
        } catch (Throwable t) {
            statsOk = false;
        }
    }

    /** As estat\u00edsticas do Block Clover (Time, mana, atributo) foram encontradas. */
    public static boolean isAvailable() {
        init();
        return statsOk;
    }

    private static Object stats(LivingEntity e) throws Exception {
        return statsGet.invoke(null, e);
    }

    /** Grim\u00f3rio de Tempo equipado e em modo de combate (\u00e9 quando o livro aparece). S\u00f3 o atributo principal. */
    public static boolean hasTimeGrimoireOut(LivingEntity entity) {
        init();
        if (!statsOk) return false;
        try {
            Object st = stats(entity);
            if (st == null) return false;
            return "time".equalsIgnoreCase(String.valueOf(getAttribute.invoke(st)))
                    && (Boolean) hasGrimoire.invoke(st)
                    && (Boolean) inCombat.invoke(st);
        } catch (Throwable t) {
            return false;
        }
    }

    /** O jogador est\u00e1 com o soco do Time Steal armado (habilidade equipada e "ativa", antes de acertar). */
    public static boolean isStealPunchActive(PlayerEntity player) {
        init();
        if (!abilitiesOk || stealIsContinuous == null) return false;
        try {
            Object data = abilityGet.invoke(null, player);
            if (data == null) return false;
            Object[] equipped = (Object[]) getEquipped.invoke(data, categoryAll);
            if (equipped == null) return false;
            for (Object ab : equipped) {
                if (ab == null || !stealClass.isInstance(ab)) continue;
                Object active = stealIsContinuous.invoke(ab);
                if (active instanceof Boolean && (Boolean) active) return true;
            }
        } catch (Throwable t) {
            // sem como saber: n\u00e3o interfere
        }
        return false;
    }

    /**
     * Escreve no log o que foi encontrado no Block Clover. Se o mod mudar numa atualiza\u00e7\u00e3o, \u00e9 aqui que aparece
     * o que parou de funcionar (antes as falhas eram silenciosas).
     */
    public static void logStatus() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            LOGGER.warn("[ChronoBubble] Block Clover nao esta instalado: so a parte visual do addon vai funcionar.");
            return;
        }
        init();
        LOGGER.info("[ChronoBubble] Block Clover: estatisticas {}, habilidades {}, sincronizacao {}, Time Steal {}, passivas {}",
                ok(statsOk), ok(abilitiesOk), ok(syncOk), ok(stealIsContinuous != null), ok(passiveClass != null));
        if (!statsOk || !abilitiesOk || !syncOk || stealIsContinuous == null || passiveClass == null) {
            LOGGER.warn("[ChronoBubble] Parte do Block Clover nao foi encontrada (versao diferente?). "
                    + "Os recursos que dependem dela ficam desligados.");
        }
    }

    private static String ok(boolean b) {
        return b ? "OK" : "FALHOU";
    }

    /** A inst\u00e2ncia desbloqueada de uma passiva (onde o Block Clover guarda o estado dela), ou null. */
    private static Object unlockedPassive(PlayerEntity player, Class<?> cls) throws Exception {
        if (passiveClass == null || cls == null) return null;
        Object data = abilityGet.invoke(null, player);
        if (data == null) return null;
        java.util.function.Predicate<Object> isIt = cls::isInstance;
        List<?> found = (List<?>) getUnlockedFiltered.invoke(data, isIt);
        return found == null || found.isEmpty() ? null : found.get(0);
    }

    /** A passiva est\u00e1 desbloqueada e n\u00e3o est\u00e1 pausada (\u00e9 assim que o Block Clover decide se ela age). */
    private static boolean passiveActive(PlayerEntity player, Class<?> cls) {
        init();
        try {
            Object p = unlockedPassive(player, cls);
            return p != null && !(Boolean) passiveIsPaused.invoke(p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** As passivas foram encontradas no Block Clover (sen\u00e3o as regras que dependem delas ficam desligadas). */
    public static boolean passivesAvailable() {
        init();
        return passiveClass != null;
    }

    /** A passiva Time Magic Zone est\u00e1 ativa. */
    public static boolean hasTimeMagicZone(PlayerEntity player) {
        return passiveActive(player, magicZoneClass);
    }

    /**
     * A Time Ressurection est\u00e1 pronta para salvar o jogador, pelas regras do pr\u00f3prio Block Clover:
     * passiva ativa, carga cheia e mais de {@link #RESSURECTION_MIN_TIME} de Time.
     */
    public static boolean isRessurectionReady(PlayerEntity player) {
        init();
        try {
            Object p = unlockedPassive(player, ressurectionClass);
            if (p == null || (Boolean) passiveIsPaused.invoke(p)) return false;
            return (Double) ressurectionStrain.invoke(p) >= RESSURECTION_FULL_STRAIN && getTime(player) > RESSURECTION_MIN_TIME;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Zera a carga da Time Ressurection (como o Block Clover faz depois de us\u00e1-la). */
    public static void resetRessurection(PlayerEntity player) {
        init();
        try {
            Object p = unlockedPassive(player, ressurectionClass);
            if (p != null) ressurectionReset.invoke(p, player, true);
        } catch (Throwable ignored) {
        }
    }

    /** O jogador tem o Chrono Stasis liberado (some quando ele est\u00e1 "renascido"). */
    public static boolean hasChronoStasis(PlayerEntity player) {
        init();
        if (!abilitiesOk) return false;
        try {
            Object data = abilityGet.invoke(null, player);
            Object core = coreOf(KEY_STASIS);
            return data != null && core != null && (Boolean) hasUnlocked.invoke(data, core);
        } catch (Throwable t) {
            return false;
        }
    }

    /** O jogador est\u00e1 com a combat bar ativa. */
    public static boolean isInCombat(LivingEntity entity) {
        init();
        if (!statsOk) return false;
        try {
            Object st = stats(entity);
            return st != null && (Boolean) inCombat.invoke(st);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Usa magia de Tempo como atributo principal ou secund\u00e1rio. */
    public static boolean isTimeUser(LivingEntity entity) {
        init();
        if (!statsOk) return false;
        try {
            Object st = stats(entity);
            if (st == null) return false;
            return "time".equalsIgnoreCase(String.valueOf(getAttribute.invoke(st)))
                    || "time".equalsIgnoreCase(String.valueOf(getSecondAttribute.invoke(st)));
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getTime(LivingEntity entity) {
        init();
        if (!statsOk) return -1;
        try {
            Object st = stats(entity);
            return st == null ? -1 : (Integer) getTime.invoke(st);
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void setTime(LivingEntity entity, int value) {
        init();
        if (!statsOk) return;
        try {
            Object st = stats(entity);
            if (st != null) setTime.invoke(st, value);
        } catch (Throwable ignored) {
        }
    }

    public static float getMana(LivingEntity entity) {
        init();
        if (!statsOk) return -1.0F;
        try {
            Object st = stats(entity);
            return st == null ? -1.0F : (Float) getMana.invoke(st);
        } catch (Throwable t) {
            return -1.0F;
        }
    }

    /** Cobra o custo do arremesso: mana, tempo e a experi\u00eancia que o soco original tamb\u00e9m d\u00e1. */
    public static void spend(LivingEntity entity, float mana, int time, int experience) {
        init();
        if (!statsOk) return;
        try {
            Object st = stats(entity);
            if (st == null) return;
            alterMana.invoke(st, -mana);
            alterExperience.invoke(st, experience);
            alterTime.invoke(st, -time);
        } catch (Throwable ignored) {
        }
    }

    /** Nome da classe da habilidade de um AbilityUseEvent (por reflex\u00e3o), ou "" se n\u00e3o der. */
    public static String abilityClassName(Object abilityEvent) {
        try {
            Object ability = abilityEvent.getClass().getMethod("getAbility").invoke(abilityEvent);
            return ability == null ? "" : ability.getClass().getName();
        } catch (Throwable t) {
            return "";
        }
    }

    private static Object coreOf(String key) {
        return KEY_STASIS.equals(key) ? coreStasis : coreGrigora;
    }

    /** Remove Chrono Stasis e Chrono Stasis Grigora do jogador. Devolve quais ele realmente tinha liberadas. */
    public static List<String> lockStasisAbilities(PlayerEntity player) {
        List<String> removed = new ArrayList<>();
        init();
        if (!abilitiesOk) return removed;
        try {
            Object data = abilityGet.invoke(null, player);
            if (data == null) return removed;
            for (String key : new String[] { KEY_STASIS, KEY_GRIGORA }) {
                Object core = coreOf(key);
                if (core == null) continue;
                if ((Boolean) hasUnlocked.invoke(data, core)) {
                    remUnlocked.invoke(data, core);
                    removed.add(key);
                }
                remEquipped.invoke(data, core);
            }
        } catch (Throwable ignored) {
        }
        return removed;
    }

    /** Devolve as habilidades removidas antes (o jogador precisa equip\u00e1-las de novo nos slots). */
    public static void restoreAbilities(PlayerEntity player, Collection<String> keys) {
        init();
        if (!abilitiesOk) return;
        try {
            Object data = abilityGet.invoke(null, player);
            if (data == null) return;
            for (String key : keys) {
                Object core = coreOf(key);
                if (core != null && !(Boolean) hasUnlocked.invoke(data, core)) addUnlocked.invoke(data, player, core);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Reenvia ao cliente os dados de tempo e de habilidades do jogador. */
    public static void syncToClient(PlayerEntity player) {
        init();
        if (!syncOk) return;
        try {
            Object st = stats(player);
            if (st != null) sendTo.invoke(null, statsPacket.newInstance(player.getId(), st), player);
            Object data = abilityGet.invoke(null, player);
            if (data != null) sendTo.invoke(null, abilityPacket.newInstance(player.getId(), data), player);
        } catch (Throwable ignored) {
        }
    }
}
