package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AgingRule}: tempo avançado no fim do Chrono Stasis (padrão: 3 Time por ponto de vida). */
class AgingRuleTest {
    @Test
    void enoughTimeKills() {
        AgingRule.Result r = AgingRule.resolve(20.0F, 20.0F, 100, 3.0F, false, 0.2F);   // zumbi: 60 de Time
        assertTrue(r.kill);
        assertEquals(60, r.timeCost);
    }

    @Test
    void notEnoughTimeDealsWhatItPaysFor() {
        AgingRule.Result r = AgingRule.resolve(20.0F, 20.0F, 30, 3.0F, false, 0.2F);    // 30 Time = 10 de vida
        assertFalse(r.kill);
        assertEquals(10.0F, r.damage);
        assertEquals(30, r.timeCost);
    }

    @Test
    void woundedTargetIsCheaper() {
        AgingRule.Result r = AgingRule.resolve(4.5F, 20.0F, 100, 3.0F, false, 0.2F);
        assertTrue(r.kill);
        assertEquals(14, r.timeCost);                                                    // 4,5 x 3 = 13,5 -> 14
    }

    @Test
    void bossTakesCappedDamageAndNeverDies() {
        AgingRule.Result r = AgingRule.resolve(300.0F, 300.0F, 100000, 3.0F, true, 0.2F);
        assertFalse(r.kill);
        assertEquals(60.0F, r.damage);                                                   // 20% de 300
        AgingRule.Result low = AgingRule.resolve(5.0F, 300.0F, 100000, 3.0F, true, 0.2F);
        assertFalse(low.kill);
        assertEquals(4.0F, low.damage);                                                  // fica com 1 de vida
    }

    @Test
    void noTimeDoesNothing() {
        assertSame(AgingRule.NOTHING, AgingRule.resolve(20.0F, 20.0F, 0, 3.0F, false, 0.2F));
        assertSame(AgingRule.NOTHING, AgingRule.resolve(20.0F, 20.0F, 2, 3.0F, false, 0.2F));   // não paga nem 1 de vida
    }
}
