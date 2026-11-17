package com.gabriel.chronobubble;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RebirthSlotsTest {
    @Test
    void roundTrip() {
        Map<String, Integer> slots = new LinkedHashMap<>();
        slots.put("stasis", 3);
        slots.put("grigora", 12);
        String stored = RebirthHandler.formatSlots(slots);
        assertEquals("stasis:3,grigora:12", stored);
        assertEquals(slots, RebirthHandler.parseSlots(stored));
    }

    @Test
    void emptyAndBroken() {
        assertTrue(RebirthHandler.parseSlots("").isEmpty());
        assertTrue(RebirthHandler.parseSlots(null).isEmpty());
        Map<String, Integer> slots = RebirthHandler.parseSlots("stasis:x,:4,grigora:5,lixo");
        assertEquals(1, slots.size());
        assertEquals(5, (int) slots.get("grigora"));
    }
}
