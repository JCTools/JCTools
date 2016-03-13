package org.jctools.jmh.counters;

import org.jctools.counters.FixedSizeStripedLongCounter;
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
                return new FixedSizeStripedCounter(
                    org.jctools.counters.CountersFactory.createFixedSizeStripedCounterV6(STRIPES_COUNT));
            case FixedSizeStripedV8:
                return new FixedSizeStripedCounter(
                    org.jctools.counters.CountersFactory.createFixedSizeStripedCounterV8(STRIPES_COUNT));
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

    static class FixedSizeStripedCounter implements Counter {
        private FixedSizeStripedLongCounter counter;

        public FixedSizeStripedCounter(FixedSizeStripedLongCounter impl) {
            counter = impl;
        }

        @Override
        @CompilerControl(Mode.INLINE)
        public void inc() {
            counter.inc();
        }

        @Override
        @CompilerControl(Mode.INLINE)
        public long get() {
            return counter.get();
        }
    }
}
