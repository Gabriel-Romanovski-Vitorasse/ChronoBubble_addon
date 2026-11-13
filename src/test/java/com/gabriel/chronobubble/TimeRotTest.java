package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link TimeRot}: o Time do soco do Time Steal chega aos poucos, e a soma é sempre exata. */
class TimeRotTest {
    @Test
    void threeSecondsArePulsesOfHalfASecond() {
        assertEquals(6, TimeRot.pulsesFor(60));
        assertEquals(1, TimeRot.pulsesFor(3));       // sempre pelo menos 1 pulso
    }

    @Test
    void sharesAddUpToTheTotal() {
        for (int total : new int[] { 0, 1, 7, 20, 23, 100 }) {
            int left = total, pulses = TimeRot.pulsesFor(60), sum = 0;
            while (pulses > 0) {
                int s = TimeRot.share(left, pulses);
                sum += s;
                left -= s;
                pulses--;
            }
            assertEquals(total, sum, "total " + total);
        }
    }

    @Test
    void twentyTimeOverSixPulses() {
        assertEquals(3, TimeRot.share(20, 6));        // 3, 3, 3, 3, 4, 4 ao longo dos pulsos
        assertEquals(4, TimeRot.share(8, 2));
        assertEquals(4, TimeRot.share(4, 1));         // o último leva o resto
    }
}
