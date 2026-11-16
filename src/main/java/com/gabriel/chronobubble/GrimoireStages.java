package com.gabriel.chronobubble;

/**
 * Fases do grimório enquanto o jogador está "renascido": as páginas voltam aos poucos conforme o Time sobe
 * (1, 4, 16, 64 e, no fim, o grimório completo). Sem depender do Minecraft, para dar para testar.
 */
public final class GrimoireStages {
    /** Valor de páginas que significa "grimório completo". */
    public static final int FULL = 0;

    private GrimoireStages() {
    }

    /**
     * Páginas para um certo Time.
     *
     * @return 1, 4, 16 ou 64, ou {@link #FULL} quando {@code time >= complete}
     */
    public static int pagesFor(int time, int pages4, int pages16, int pages64, int complete) {
        if (time >= complete) return FULL;
        if (time >= pages64) return 64;
        if (time >= pages16) return 16;
        if (time >= pages4) return 4;
        return 1;
    }

    /** As páginas nunca voltam a diminuir enquanto o jogador está renascido (gastar Time não tira páginas). */
    public static int keepMax(int current, int next) {
        if (current == FULL || next == FULL) return FULL;
        return Math.max(Math.max(1, current), next);
    }
}
