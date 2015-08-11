package org.jctools.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class Pow2Test {

    @Test
    public void testAlign() {
        assertEquals(4, Pow2.align(2, 4));
        assertEquals(4, Pow2.align(4, 4));
    }
    
    @Test
    public void testRound() {
        assertEquals(4, Pow2.roundToPowerOfTwo(4));
        assertEquals(4, Pow2.roundToPowerOfTwo(3));
    }
}
