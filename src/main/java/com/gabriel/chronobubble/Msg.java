package com.gabriel.chronobubble;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.Locale;

/**
 * Mensagens traduzíveis (assets/chrono_bubble/lang). O servidor manda só a chave e os valores;
 * cada cliente mostra no idioma dele. As chaves ficam em "msg.chrono_bubble.<nome>".
 */
public final class Msg {
    private static final String PREFIX = "msg." + ChronoBubbleMod.MODID + ".";

    private Msg() {
    }

    /** Texto traduzível com uma cor só (códigos § dentro do arquivo de idioma não passam para os valores %s). */
    public static IFormattableTextComponent tr(TextFormatting color, String key, Object... args) {
        return new TranslationTextComponent(PREFIX + key, args).withStyle(color);
    }

    /** Mostra a mensagem na action bar (acima da hotbar). */
    public static void bar(PlayerEntity player, TextFormatting color, String key, Object... args) {
        player.displayClientMessage(tr(color, key, args), true);
    }

    /** Número com uma casa decimal, sempre com ponto (não depende do idioma do sistema). */
    public static String f1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** Mana por segundo sem cortar as casas decimais (1.5 não vira 1). */
    public static String rate(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : f1(value);
    }
}
