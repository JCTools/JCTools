package org.jctools.util;

public class Pow2 {
    public static int findNextPositivePowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    public static boolean isPowerOf2(final int value) {
        return (value & (value - 1)) == 0;
    }
}
