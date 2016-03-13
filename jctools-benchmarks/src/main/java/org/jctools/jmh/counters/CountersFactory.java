package org.jctools.jmh.counters;

import org.jctools.counters.FixedSizeStripedLongCounterV6;
import org.jctools.counters.FixedSizeStripedLongCounterV8;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.CompilerControl.Mode;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Tolstopyatov Vsevolod
 */
public class CountersFactory {

    private static final int STRIPES_COUNT = Runtime.getRuntime().availableProcessors() * 4;

    public enum CounterType {
        AtomicLong,
        LongAdder,
        FixedSizeStripedV6,
        FixedSizeStripedV8
    }

    static Counter build(CounterType type) {
        switch (type) {
            case AtomicLong:
                return new AtomicLongCounter();
            case LongAdder:
                return new LongAdderCounter();
            case FixedSizeStripedV6:
                return new FixedSizeStripedCounterV6(STRIPES_COUNT);
            case FixedSizeStripedV8:
                return new FixedSizeStripedCounterV8(STRIPES_COUNT);
            default:
                throw new IllegalArgumentException();
        }
    }

    @SuppressWarnings("serial")
    static class AtomicLongCounter extends AtomicLong implements Counter {

        @Override
        @CompilerControl(Mode.INLINE)
        public void inc() {
            super.incrementAndGet();
        }
    }

    @SuppressWarnings("serial")
    static class LongAdderCounter extends LongAdder implements Counter {

        @Override
        @CompilerControl(Mode.INLINE)
        public void inc() {
            super.increment();
        }

        @Override
        @CompilerControl(Mode.INLINE)
        public long get() {
            return super.sum();
        }
    }

    static class FixedSizeStripedCounterV6 extends FixedSizeStripedLongCounterV6 implements Counter {
        public FixedSizeStripedCounterV6(int stripesCount) {
            super(stripesCount);
        }
    }

    static class FixedSizeStripedCounterV8 extends FixedSizeStripedLongCounterV8 implements Counter {
        public FixedSizeStripedCounterV8(int stripesCount) {
            super(stripesCount);
        }
    }
}
