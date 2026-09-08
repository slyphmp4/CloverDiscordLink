package com.slyph.cloverdiscordlink.util;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

public final class LinkCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private LinkCodeGenerator() {
    }

    public static String generate(int length) {
        return generate(length, SECURE_RANDOM);
    }

    static String generate(int length, RandomGenerator random) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("length must be between 4 and 10");
        }

        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
}
