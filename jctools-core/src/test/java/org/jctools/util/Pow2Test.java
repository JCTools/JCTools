package org.jctools.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class Pow2Test {
    static final int MAX_POSITIVE_POW2 = 1 << 30;

    @Test
    public void testAlign() {
        assertEquals(4, Pow2.align(2, 4));
        assertEquals(4, Pow2.align(4, 4));
    }

    @Test
    public void testRound() {
        assertEquals(4, Pow2.roundToPowerOfTwo(4));
        assertEquals(4, Pow2.roundToPowerOfTwo(3));
        assertEquals(1, Pow2.roundToPowerOfTwo(-1));
        assertEquals(1, Pow2.roundToPowerOfTwo(-132340293));
        assertEquals(MAX_POSITIVE_POW2, Pow2.roundToPowerOfTwo(MAX_POSITIVE_POW2));
    }
    @Test(expected=IllegalArgumentException.class)
    public void testMaxRoundException() {
        Pow2.roundToPowerOfTwo(MAX_POSITIVE_POW2 + 1);
        fail();
    }
}
