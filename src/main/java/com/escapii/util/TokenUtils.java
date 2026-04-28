package com.escapii.util;

import java.util.UUID;

public final class TokenUtils {

    private TokenUtils() {}

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
