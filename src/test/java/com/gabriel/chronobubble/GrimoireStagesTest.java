package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrimoireStagesTest {
    private static int pages(int time) {
        return GrimoireStages.pagesFor(time, 250, 500, 750, 1000);
    }

    @Test
    void phases() {
        assertEquals(1, pages(0));
        assertEquals(1, pages(249));
        assertEquals(4, pages(250));
        assertEquals(4, pages(499));
        assertEquals(16, pages(500));
        assertEquals(16, pages(749));
        assertEquals(64, pages(750));
        assertEquals(64, pages(999));
        assertEquals(GrimoireStages.FULL, pages(1000));
        assertEquals(GrimoireStages.FULL, pages(5000));
    }

    @Test
    void completeBelowAStageWins() {
        // um servidor com complete_time menor que as fases pula direto para o grimório completo
        assertEquals(GrimoireStages.FULL, GrimoireStages.pagesFor(200, 250, 500, 750, 200));
    }

    @Test
    void pagesNeverShrink() {
        assertEquals(16, GrimoireStages.keepMax(16, 4));
        assertEquals(64, GrimoireStages.keepMax(16, 64));
        assertEquals(1, GrimoireStages.keepMax(0 + 1, 1));
        assertEquals(GrimoireStages.FULL, GrimoireStages.keepMax(64, GrimoireStages.FULL));
    }
}
