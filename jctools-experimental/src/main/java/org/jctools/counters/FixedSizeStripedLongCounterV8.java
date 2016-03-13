package org.jctools.counters;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Wait-free implementation of striped counter using
 * Java 8 Unsafe intrinsics (lock addq and lock xchg).
 *
 * @author Tolstopyatov Vsevolod
 */
public class FixedSizeStripedLongCounterV8 extends FixedSizeStripedLongCounter {

    public FixedSizeStripedLongCounterV8(int stripesCount) {
        super(stripesCount);
    }

    @Override
    protected void inc(long offset, long delta) {
        UNSAFE.getAndAddLong(cells, offset, delta);
    }

    @Override
    protected long getAndReset(long offset) {
        return UNSAFE.getAndSetLong(cells, offset, 0L);
    }
}