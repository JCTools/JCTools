package org.jctools.util;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class VolatileLongCellP0 {
    long p0, p1, p2, p3, p4, p5, p6;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class VolatileLongCellValue extends VolatileLongCellP0 {
    protected volatile long value;
}

public final class VolatileLongCell extends VolatileLongCellValue {
    long p0, p1, p2, p3, p4, p5, p6;
    long p10, p11, p12, p13, p14, p15, p16, p17;
    private final static long VALUE_OFFSET = fieldOffset(VolatileLongCellValue.class, "value");

    public VolatileLongCell() {
        this(0L);
    }

    public VolatileLongCell(long v) {
        lazySet(v);
    }

    public void lazySet(long v) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, VALUE_OFFSET, v);
    }

    public void set(long v) {
        this.value = v;
    }

    public long get() {
        return this.value;
    }
}
