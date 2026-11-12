package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChronoThrowHandler#actsOn}: qual evento de uso de habilidade o addon trata. Imita os nomes do Block Clover:
 * {@code AbilityUseEvent} (soco: Chrono Stasis, Time Steal), {@code AbilityUseEvent$Pre} e {@code AbilityUseEvent$Post}
 * (comuns: Time Hop, Grigora).
 */
class AbilityEventFilterTest {
    static class AbilityUseEvent {
        static class Pre extends AbilityUseEvent {
        }

        static class Post extends AbilityUseEvent {
        }
    }

    @Test
    void baseEventFromPunchAbilitiesIsHandled() {
        // regressão: escutar só o Pre deixava os arremessos (Shift + Chrono Stasis / Time Steal) sem efeito
        assertTrue(ChronoThrowHandler.actsOn(new AbilityUseEvent()));
    }

    @Test
    void preEventIsHandled() {
        assertTrue(ChronoThrowHandler.actsOn(new AbilityUseEvent.Pre()));
    }

    @Test
    void postEventIsIgnored() {
        // regressão: o Post do Time Hop chega com o jogador já teleportado e apagava o ponto de partida
        assertFalse(ChronoThrowHandler.actsOn(new AbilityUseEvent.Post()));
    }
}
