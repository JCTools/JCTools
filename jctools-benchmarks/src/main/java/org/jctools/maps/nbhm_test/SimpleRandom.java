package org.jctools.maps.nbhm_test;

import java.util.concurrent.atomic.AtomicLong;

// Fairly fast random numbers
public final class SimpleRandom {
    private final static long multiplier = 0x5DEECE66DL;
    private final static long addend = 0xBL;
    private final static long mask = (1L << 48) - 1;
    static final AtomicLong seq = new AtomicLong(-715159705);
    private long seed;

    public SimpleRandom() {
        seed = System.nanoTime() + seq.getAndAdd(129);
    }

    public int nextInt() {
        return next();
    }

    public int next() {
        long nextseed = (seed * multiplier + addend) & mask;
        seed = nextseed;
        return ((int) (nextseed >>> 17)) & 0x7FFFFFFF;
    }
}
