package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link WitherStasis#duration}: quanto o Wither fica congelado, com os valores padrão da config (sem arquivo
 * carregado, o Forge devolve o padrão): 30% do normal, no máximo 60 ticks, metade na segunda fase, mínimo 10.
 */
class WitherStasisTest {
    @Test
    void onlyAFractionOfTheNormalFreeze() {
        assertEquals(30, WitherStasis.duration(100, false));      // esfera de 5 s: 1,5 s no Wither
    }

    @Test
    void secondPhaseHalvesIt() {
        assertEquals(15, WitherStasis.duration(100, true));
    }

    @Test
    void sustainedAndGrigoraAreCapped() {
        assertEquals(60, WitherStasis.duration(Integer.MAX_VALUE / 2, false));
        assertEquals(30, WitherStasis.duration(Integer.MAX_VALUE / 2, true));
    }

    @Test
    void neverShorterThanHalfASecond() {
        assertEquals(10, WitherStasis.duration(5, true));
    }
}
