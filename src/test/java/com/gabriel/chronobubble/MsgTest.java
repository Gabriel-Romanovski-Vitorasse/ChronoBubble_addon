package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MsgTest {
    @Test
    void rateKeepsDecimals() {
        assertEquals("1.5", Msg.rate(1.5));
        assertEquals("0.3", Msg.rate(0.25 + 0.05));
    }

    @Test
    void rateDropsZeroDecimals() {
        assertEquals("3", Msg.rate(3.0));
        assertEquals("0", Msg.rate(0.0));
    }

    @Test
    void oneDecimalAlwaysUsesDot() {
        assertEquals("2.5", Msg.f1(2.5));
        assertEquals("10.0", Msg.f1(10));
    }
}
