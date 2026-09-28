package com.escapii.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadezTest {

    @Test
    void recUzBrojDana() {
        assertEquals("dan", Padez.dan(1));
        assertEquals("dana", Padez.dan(2));
        assertEquals("dana", Padez.dan(4));
        assertEquals("dana", Padez.dan(5));
        assertEquals("dana", Padez.dan(7));
        assertEquals("dana", Padez.dan(11));
        assertEquals("dana", Padez.dan(15));
        assertEquals("dan", Padez.dan(21));
        assertEquals("dana", Padez.dan(22));
        assertEquals("dan", Padez.dan(31));
        assertEquals("dan", Padez.dan(101));
        assertEquals("dana", Padez.dan(111));
        assertEquals("dana", Padez.dan(0));
        assertEquals("dan", Padez.dan(-1));
    }
}
