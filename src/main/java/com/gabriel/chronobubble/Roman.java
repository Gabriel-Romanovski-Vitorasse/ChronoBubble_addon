package com.gabriel.chronobubble;

/** Números romanos (usados nos números que sobem ao roubar Time). */
public final class Roman {
    private static final int[] VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] SYMBOLS = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };

    private Roman() {
    }

    /** {@code n} em números romanos; vazio para zero ou negativo. Acima de 3999 repete o M (ex.: 5000 = MMMMM). */
    public static String of(int n) {
        if (n <= 0) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                b.append(SYMBOLS[i]);
                n -= VALUES[i];
            }
        }
        return b.toString();
    }
}
