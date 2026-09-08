package com.slyph.cloverdiscordlink.util;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCodeGeneratorTest {

    @Test
    void generatesRequestedNumberOfDigits() {
        String code = LinkCodeGenerator.generate(8, new Random(42));

        assertEquals(8, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
    }

    @Test
    void rejectsUnsafeLengths() {
        assertThrows(IllegalArgumentException.class, () -> LinkCodeGenerator.generate(3));
        assertThrows(IllegalArgumentException.class, () -> LinkCodeGenerator.generate(11));
    }
}
