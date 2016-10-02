package org.jctools.jmh.counters;

import static org.jctools.counters.CountersFactory.createFixedSizeStripedCounterV6;
import static org.jctools.counters.CountersFactory.createFixedSizeStripedCounterV8;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.jctools.counters.FixedSizeStripedLongCounter;
import org.jctools.maps.ConcurrentAutoTable;

/**
 * @author Tolstopyatov Vsevolod
 */
public class CountersFactory {

    public enum CounterType {
        AtomicLong,
        LongAdder,
        FixedSizeStripedV6,
        FixedSizeStripedV8,
        CAT
    }

    static Counter build(CounterType type, int stripes) {
        switch (type) {
        case AtomicLong:
            return new AtomicLongCounter();
        case LongAdder:
            return new LongAdderCounter();
        case FixedSizeStripedV6:
            return new FixedSizeStripedCounter(createFixedSizeStripedCounterV6(stripes));
        case FixedSizeStripedV8:
            return new FixedSizeStripedCounter(createFixedSizeStripedCounterV8(stripes));
        case CAT:
            return new ConcurrentAutoTableCounter();
        default:
            throw new IllegalArgumentException();
        }
    }

    // For consistent and fair benchmarking, always use counter as field

    static class AtomicLongCounter extends Counter {
        private final AtomicLong counter = new AtomicLong();

        @Override
        public void inc() {
            counter.incrementAndGet();
        }

        @Override
        public long get() {
            return counter.get();
        }
    }

    static class LongAdderCounter extends Counter {
        private final LongAdder counter = new LongAdder();

        @Override
        public void inc() {
            counter.increment();
        }

        @Override
        public long get() {
            return counter.sum();
        }
    }

    static class FixedSizeStripedCounter extends Counter {
        private final FixedSizeStripedLongCounter counter;

        public FixedSizeStripedCounter(FixedSizeStripedLongCounter impl) {
            counter = impl;
        }

        @Override
        public void inc() {
            counter.inc();
        }

        @Override
        public long get() {
            return counter.get();
        }
    }

    static class ConcurrentAutoTableCounter extends Counter {
        private final ConcurrentAutoTable counter;

        public ConcurrentAutoTableCounter() {
            counter = new ConcurrentAutoTable();
        }

        @Override
        public void inc() {
            counter.add(1);
        }

        @Override
        public long get() {
            return counter.get();
        }
    }
}
