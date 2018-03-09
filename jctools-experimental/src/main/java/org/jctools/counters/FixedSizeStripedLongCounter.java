package org.jctools.counters;

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.concurrent.ThreadLocalRandom;

import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;

/**
 * Basic class representing static striped long counter with
 * common mechanics for implementors.
 *
 * @author Tolstopyatov Vsevolod
 */
abstract class FixedSizeStripedLongCounterPrePad {
    long l01, l02, l03, l04, l05, l06, l07, l08;
    long l9, l10, l11, l12, l13, l14, l15;
}
abstract class FixedSizeStripedLongCounterFields extends FixedSizeStripedLongCounterPrePad {
    protected static final int CACHE_LINE_IN_LONGS = PortableJvmInfo.CACHE_LINE_SIZE / 8;
    // place first element at the end of the cache line of the array object
    protected static final long COUNTER_ARRAY_BASE = Math.max(UNSAFE.arrayBaseOffset(long[].class), PortableJvmInfo.CACHE_LINE_SIZE - 8);
    // element shift is enlarged to include the padding, still aligned to long
    protected static final long ELEMENT_SHIFT = Integer.numberOfTrailingZeros(PortableJvmInfo.CACHE_LINE_SIZE);
    
    // we pad each element in the array to effectively write a counter in each cache line
    protected final long[] cells;
    protected final int mask;
    protected FixedSizeStripedLongCounterFields(int stripesCount) {
        if (stripesCount <= 0) {
            throw new IllegalArgumentException("Expecting a stripesCount that is larger than 0");
        }
        int size = Pow2.roundToPowerOfTwo(stripesCount);
        cells = new long[CACHE_LINE_IN_LONGS * size];
        mask = (size - 1);
    }
}

public abstract class FixedSizeStripedLongCounter extends FixedSizeStripedLongCounterFields implements Counter {
    long l02, l03, l04, l05, l06, l07, l08;
    long l9, l10, l11, l12, l13, l14, l15, l16;

    private static final long PROBE = getProbeOffset();

    private static long getProbeOffset() {
        try {
            return UNSAFE.objectFieldOffset(Thread.class.getDeclaredField("threadLocalRandomProbe"));

        } catch (NoSuchFieldException e) {
            return -1L;
        }
    }

    public FixedSizeStripedLongCounter(int stripesCount) {
        super(stripesCount);
    }

    @Override
    public void inc() {
        inc(1L);
    }

    @Override
    public void inc(long delta) {
        inc(cells, counterOffset(index()), delta);
    }

    @Override
    public long get() {
        long result = 0L;
        long[] cells = this.cells;
        int length = mask + 1;
        for (int i = 0; i < length; i++) {
            result += UNSAFE.getLongVolatile(cells, counterOffset(i));
        }
        return result;
    }

    private long counterOffset(long i) {
        return COUNTER_ARRAY_BASE + (i << ELEMENT_SHIFT);
    }

    @Override
    public long getAndReset() {
        long result = 0L;
        long[] cells = this.cells;
        int length = mask + 1;
        for (int i = 0; i < length; i++) {
            result += getAndReset(cells, counterOffset(i));
        }
        return result;
    }

    protected abstract void inc(long[] cells, long offset, long value);

    protected abstract long getAndReset(long[] cells, long offset);

    private int index() {
        return probe() & mask;
    }


    /**
     * Returns the probe value for the current thread.
     * If target JDK version is 7 or higher, than ThreadLocalRandom-specific
     * value will be used, xorshift with thread id otherwise.
     */
    private int probe() {
        // Fast path for reliable well-distributed probe, available from JDK 7+.
        // As long as PROBE is final this branch will be inlined.
        if (PROBE != -1) {
            int probe;
            if ((probe = UNSAFE.getInt(Thread.currentThread(), PROBE)) == 0) {
                ThreadLocalRandom.current(); // force initialization
                probe = UNSAFE.getInt(Thread.currentThread(), PROBE);
            }
            return probe;
        }

        /*
         * Else use much worse (for values distribution) method:
         * Mix thread id with golden ratio and then xorshift it
         * to spread consecutive ids (see Knuth multiplicative method as reference).
         */
        int probe = (int) ((Thread.currentThread().getId() * 0x9e3779b9) & Integer.MAX_VALUE);
        // xorshift
        probe ^= probe << 13;
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        return probe;
    }
}

