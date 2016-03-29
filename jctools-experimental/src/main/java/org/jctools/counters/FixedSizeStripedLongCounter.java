package org.jctools.counters;

import org.jctools.util.JvmInfo;
import org.jctools.util.Pow2;
import sun.misc.Unsafe;

import java.util.concurrent.ThreadLocalRandom;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Basic class representing static striped long counter with
 * common mechanics for implementors.
 *
 * @author Tolstopyatov Vsevolod
 */
public abstract class FixedSizeStripedLongCounter extends PaddedHeader implements Counter {

    private static final int LONG_ARRAY_BASE = Unsafe.ARRAY_LONG_BASE_OFFSET;
    private static final int LONG_SCALE_SHIFT = 3;
    private static final int PADDING = JvmInfo.CACHE_LINE_SIZE / 8;

    protected final long[] cells;
    protected final int mask;

    public FixedSizeStripedLongCounter(int stripesCount) {
        int size = Pow2.roundToPowerOfTwo(PADDING * stripesCount);
        cells = new long[size];
        /*
         * Trick to avoid cells.length modulo + padding alignment.
         * It's faster to use precalculated mask, because
         * JIT can't deduce that cells is final field, throw away length dec call
         * and fold it with consecutive 'and' op for alignment.
         */
        mask = (cells.length - 1) & ~(PADDING - 1);
    }

    @Override
    public void inc() {
        inc(1L);
    }

    @Override
    public void inc(long delta) {
        inc(index(), delta);
    }

    @Override
    public long get() {
        long result = 0L;
        for (int i = 0; i < cells.length; i += PADDING) {
            result += UNSAFE.getLongVolatile(cells, LONG_ARRAY_BASE + (i << LONG_SCALE_SHIFT));
        }
        return result;
    }

    @Override
    public long getAndReset() {
        long result = 0L;
        for (int i = 0; i < cells.length; i += PADDING) {
            result += getAndReset(LONG_ARRAY_BASE + (i << LONG_SCALE_SHIFT));
        }
        return result;
    }

    protected abstract void inc(long offset, long value);

    protected abstract long getAndReset(long offset);

    private int index() {
        int index = probe() & mask;
        return LONG_ARRAY_BASE + (index << LONG_SCALE_SHIFT);
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

    private static final long PROBE = getProbeOffset();

    private static long getProbeOffset() {
        try {
            Class<?> tk = Thread.class;
            return UNSAFE.objectFieldOffset(tk.getDeclaredField("threadLocalRandomProbe"));

        } catch (NoSuchFieldException e) {
            return -1L;
        }
    }
}

abstract class PaddedHeader {
    long l01, l02, l03, l04, l05, l06, l07, l08;
    long l9, l10, l11, l12, l13, l14, l15, l16;
}
