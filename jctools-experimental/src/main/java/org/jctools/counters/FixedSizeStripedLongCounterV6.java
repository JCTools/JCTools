package org.jctools.counters;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * Lock-free implementation of striped counter using
 * CAS primitives.
 *
 * @author Tolstopyatov Vsevolod
 */
public class FixedSizeStripedLongCounterV6 extends FixedSizeStripedLongCounter {

    public FixedSizeStripedLongCounterV6(int stripesCount) {
        super(stripesCount);
    }

    @Override
    protected void inc(long offset, long delta) {
        // Local load to avoid reloading after volatile read
        long[] cells = this.cells;
        long v;
        do {
            v = UNSAFE.getLongVolatile(cells, offset);
        } while (!UNSAFE.compareAndSwapLong(cells, offset, v, v + delta));
    }

    @Override
    protected long getAndReset(long offset) {
        // Local load to avoid reloading after volatile read
        long[] cells = this.cells;
        long v;
        do {
            v = UNSAFE.getLongVolatile(cells, offset);
        } while (!UNSAFE.compareAndSwapLong(cells, offset, v, 0L));

        return v;
    }
}
