package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RomanTest {
    @Test
    void basicNumbers() {
        assertEquals("I", Roman.of(1));
        assertEquals("V", Roman.of(5));
        assertEquals("X", Roman.of(10));
        assertEquals("XX", Roman.of(20));
    }

    @Test
    void subtractiveForms() {
        assertEquals("IV", Roman.of(4));
        assertEquals("IX", Roman.of(9));
        assertEquals("XL", Roman.of(40));
        assertEquals("XC", Roman.of(90));
        assertEquals("CD", Roman.of(400));
        assertEquals("CM", Roman.of(900));
    }

    @Test
    void mixedNumbers() {
        assertEquals("XIV", Roman.of(14));
        assertEquals("MCMXCIV", Roman.of(1994));
        assertEquals("MMMCMXCIX", Roman.of(3999));
    }

    @Test
    void zeroAndNegativeAreEmpty() {
        assertEquals("", Roman.of(0));
        assertEquals("", Roman.of(-7));
    }
}
