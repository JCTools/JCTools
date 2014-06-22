package org.jctools.util;

/**
 * Power of 2 utility functions.
 */
public class Pow2 {

    /**
     * Find the next larger positive power of two value up from the given value. If value is a power of two then
     * this value will be returned.
     *
     * @param value from which next positive power of two will be found.
     * @return the next positive power of 2 or this value if it is a power of 2.
     */
    public static int findNextPositivePowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    /**
     * Is this value a power of two.
     *
     * @param value to be tested to see if it is a power of two.
     * @return true if the value is a power of 2 otherwise false.
     */
    public static boolean isPowerOfTwo(final int value) {
        return (value & (value - 1)) == 0;
    }
}
