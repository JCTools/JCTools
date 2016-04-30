package org.jctools.jmh.counters;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Group)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
public class CountersBenchmark {

    private Counter counter;

    @Param
    CountersFactory.CounterType counterType;

    @Param("0")
    int stripes;

    @Setup
    public void buildCounter() {
        if (stripes <= 0)
            stripes = Runtime.getRuntime().availableProcessors();
        counter = CountersFactory.build(counterType, stripes);
    }

    @Benchmark
    @Group("rw")
    public void inc() {
        counter.inc();
    }

    @Benchmark
    @Group("rw")
    public long get() {
        return counter.get();
    }
}
