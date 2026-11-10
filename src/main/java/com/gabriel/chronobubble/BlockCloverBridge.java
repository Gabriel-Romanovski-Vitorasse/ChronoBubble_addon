package com.gabriel.chronobubble;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

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
    public static final String KEY_STASIS = "stasis";
    public static final String KEY_GRIGORA = "grigora";

    private static boolean tried;
    private static boolean statsOk;
    private static boolean abilitiesOk;
    private static boolean syncOk;

    private static Method statsGet, getAttribute, getSecondAttribute, hasGrimoire, inCombat, getTime, setTime;
    private static Method getMana, alterMana, alterTime, alterExperience;
    private static Method abilityGet, hasUnlocked, remUnlocked, remEquipped, addUnlocked;
    private static Method sendTo;
    private static Constructor<?> statsPacket, abilityPacket;
    private static Object coreStasis, coreGrigora;

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> capStats = Class.forName("com.yuanno.block_clover.data.entity.EntityStatsCapability");
            Class<?> stats = Class.forName("com.yuanno.block_clover.data.entity.IEntityStats");
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
                Class<?> capAbility = Class.forName("com.yuanno.block_clover.data.ability.AbilityDataCapability");
                Class<?> data = Class.forName("com.yuanno.block_clover.data.ability.IAbilityData");
                Class<?> core = Class.forName("com.yuanno.block_clover.api.ability.AbilityCore");
                abilityGet = capAbility.getMethod("get", LivingEntity.class);
                hasUnlocked = data.getMethod("hasUnlockedAbility", core);
                remUnlocked = data.getMethod("removeUnlockedAbility", core);
                remEquipped = data.getMethod("removeEquippedAbility", core);
                addUnlocked = data.getMethod("addUnlockedAbility", PlayerEntity.class, core);
                coreStasis = Class.forName("com.yuanno.block_clover.spells.time.ChronoStasisAbility").getField("INSTANCE").get(null);
                coreGrigora = Class.forName("com.yuanno.block_clover.spells.time.ChronoStasisGrigoraAbility").getField("INSTANCE").get(null);
                abilitiesOk = true;

                Class<?> ph = Class.forName("com.yuanno.block_clover.networking.PacketHandler");
                sendTo = ph.getMethod("sendTo", Object.class, PlayerEntity.class);
                statsPacket = Class.forName("com.yuanno.block_clover.networking.server.SSyncEntityStatsPacket")
                        .getConstructor(int.class, stats);
                abilityPacket = Class.forName("com.yuanno.block_clover.networking.server.SSyncAbilityDataPacket")
                        .getConstructor(int.class, data);
                syncOk = true;
            } catch (Throwable ignored) {
                // habilidades/sincroniza\u00e7\u00e3o indispon\u00edveis: o resto continua funcionando
            }
        } catch (Throwable t) {
            statsOk = false;
        }
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
        if (!abilitiesOk) return false;
        try {
            Object data = abilityGet.invoke(null, player);
            if (data == null) return false;
            Class<?> dataIface = Class.forName("com.yuanno.block_clover.data.ability.IAbilityData");
            Class<?> catClass = Class.forName("com.yuanno.block_clover.api.ability.AbilityCategories$AbilityCategory");
            Object all = catClass.getField("ALL").get(null);
            Object[] equipped = (Object[]) dataIface.getMethod("getEquippedAbilities", catClass).invoke(data, all);
            if (equipped == null) return false;
            for (Object ab : equipped) {
                if (ab == null) continue;
                if (!ab.getClass().getName().equals("com.yuanno.block_clover.spells.time.TimeStealAbility")) continue;
                Object active = ab.getClass().getMethod("isContinuous").invoke(ab);
                if (active instanceof Boolean && (Boolean) active) return true;
            }
        } catch (Throwable t) {
            // sem como saber: n\u00e3o interfere
        }
        return false;
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
