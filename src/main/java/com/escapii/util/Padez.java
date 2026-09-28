package com.escapii.util;

/** Oblik imenice uz broj - za tekstove koje čita kupac ili admin. */
public final class Padez {

    private Padez() {}

    /** Reč uz broj dana: 1 dan, 2 dana, 7 dana, 11 dana, 21 dan. Vraća samo reč, bez broja. */
    public static String dan(long n) {
        long a = Math.abs(n) % 100;
        long c = a % 10;
        return (c == 1 && a != 11) ? "dan" : "dana";
    }
}
