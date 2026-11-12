package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link RestorationPlanner}: o que a Restaura\u00e7\u00e3o do tempo traz de volta (grupos = tamanho em blocos). */
class RestorationPlannerTest {
    private static List<Integer> pick(List<Integer> newestFirst, int budget) {
        return RestorationPlanner.pick(newestFirst, Integer::intValue, g -> true, budget);
    }

    @Test
    void newestComeBackFirst() {
        assertEquals(Arrays.asList(3, 2), pick(Arrays.asList(3, 2, 4), 5));
    }

    @Test
    void groupThatDoesNotFitIsSkippedButSmallerOlderOnesStillCome() {
        // a explos\u00e3o de 10 n\u00e3o cabe; as quebras de 1 bloco, mais antigas, cabem
        assertEquals(Arrays.asList(2, 1, 1), pick(Arrays.asList(2, 10, 1, 1), 4));
    }

    @Test
    void groupsComeBackWholeOrNotAtAll() {
        assertEquals(Collections.emptyList(), pick(Collections.singletonList(30), 29));
    }

    @Test
    void worldCheckCanRejectAGroup() {
        // ex.: itens j\u00e1 pegos ou lugar ocupado
        List<Integer> chosen = RestorationPlanner.pick(Arrays.asList(1, 2, 3), Integer::intValue, g -> g != 2, 10);
        assertEquals(Arrays.asList(1, 3), chosen);
    }

    @Test
    void affordableBlocksFollowsTimeAndLimit() {
        assertEquals(0, RestorationPlanner.affordableBlocks(30, 30, 2, 256));     // precisa ter MAIS que o custo fixo
        assertEquals(10, RestorationPlanner.affordableBlocks(50, 30, 2, 256));
        assertEquals(256, RestorationPlanner.affordableBlocks(100000, 30, 2, 256));
        assertEquals(256, RestorationPlanner.affordableBlocks(31, 30, 0, 256));   // sem custo por bloco
    }
}
