package com.slyph.cloverdiscordlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormatterTest {

    private final TextFormatter formatter = new TextFormatter();

    @Test
    void normalizesSupportedHexFormats() {
        assertEquals("<#FF0000>text", formatter.normalize("&FF0000text"));
        assertEquals("<#00ff00>text", formatter.normalize("&#00ff00text"));
        assertEquals("<#ABCDEF>text", formatter.normalize("<#ABCDEF>text"));
    }

    @Test
    void normalizesLegacyColorsAndStyles() {
        assertEquals("<red><bold>text<reset>", formatter.normalize("&c&ltext&r"));
    }
}
