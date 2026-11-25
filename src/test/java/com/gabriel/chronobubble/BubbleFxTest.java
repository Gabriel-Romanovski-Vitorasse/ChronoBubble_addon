package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BubbleFxTest {
    @Test
    void entryOvershootsAndSettles() {
        assertEquals(0.0F, BubbleFx.easeOutBack(0.0F), 1e-4F);
        assertEquals(1.0F, BubbleFx.easeOutBack(1.0F), 1e-4F);
        float peak = 0.0F;
        for (int i = 0; i <= 100; i++) peak = Math.max(peak, BubbleFx.easeOutBack(i / 100.0F));
        assertTrue(peak > 1.03F && peak < 1.15F, "passa um pouco do tamanho: " + peak);
        assertEquals(1.0F, BubbleFx.entryFlash(0.0F), 1e-4F);
        assertEquals(0.0F, BubbleFx.entryFlash(1.0F), 1e-4F);
        assertEquals(0.0F, BubbleFx.entrySpin(1.0F), 1e-4F);
    }

    @Test
    void rippleStartsAtTheHitAndTravels() {
        // no começo, forte no ponto do golpe e nada do outro lado
        assertTrue(BubbleFx.ripple(1.0F, 0.02F) > 0.8F);
        assertTrue(BubbleFx.ripple(-1.0F, 0.02F) < 0.01F);
        // na metade da vida, a frente está perto de 90 graus do golpe
        float atFront = BubbleFx.ripple(0.0F, 0.53F);
        float atHit = BubbleFx.ripple(1.0F, 0.53F);
        assertTrue(atFront > atHit, "a onda saiu do ponto do golpe");
        // acabou
        assertEquals(0.0F, BubbleFx.ripple(1.0F, 1.0F), 1e-6F);
        assertEquals(0.0F, BubbleFx.ripple(0.0F, -0.1F), 1e-6F);
    }

    @Test
    void shardsFlyOutFallAndFade() {
        assertEquals(1.0F, BubbleFx.shardAlpha(0.0F), 1e-4F);
        assertEquals(0.0F, BubbleFx.shardAlpha(1.0F), 1e-4F);
        assertEquals(0.0F, BubbleFx.shardTravel(0.0F, 0.1F), 1e-6F);
        assertTrue(BubbleFx.shardTravel(18.0F, 0.1F) > BubbleFx.shardTravel(6.0F, 0.1F));
        assertTrue(BubbleFx.shardTravel(1000.0F, 0.1F) <= 0.8F + 1e-4F);   // desacelera, não some no infinito
        assertTrue(BubbleFx.shardDrop(10.0F) > BubbleFx.shardDrop(5.0F));
    }
}
