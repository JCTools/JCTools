/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.channels.mpsc;

import java.util.concurrent.TimeUnit;

import org.jctools.channels.proxy.ProxyChannel;
import org.jctools.channels.proxy.ProxyChannelFactory;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
public class MpscProxyChannelBenchmark {

    private static final int CAPACITY = 10000;
    private static final int PRODUCER_THREADS = 10;
    private static final int CONSUMER_THREADS = 1;

    public interface BenchIFace {

        void noArgs();

        void onePrimitiveArg(int x);

        void twoMixedLengthPrimitiveArgs(int x, long y);

        void oneObjectArg(Object x);

        void oneReferenceArg(CustomType x);

        // I'm curious is there's a performance cost of switching between the ref queue and the bytebuffer
        void tenMixedArgs(int i,
                Object o,
                long l,
                CustomType c0,
                double d,
                CustomType c1,
                float f,
                CustomType c2,
                boolean b,
                CustomType c3);

        // The first value is 'type':int that is 4 bytes which renders all of this unaligned
        void unalignedPrimitiveArgs(
                long l1,
                double d1,
                long l2,
                double d2,
                long l3,
                double d3,
                long l4,
                double d4,
                long l5,
                double d5,
                long l6,
                double d6,
                long l7,
                double d7,
                long l8,
                double d8,
                int i);

        // The first value is 'type':int that is implicit so we start with a 4 byte value first then all 8 bytes, then all 4 bytes, and so on
        void alignedPrimitiveArgs(
                int i,
                long l1,
                double d1,
                long l2,
                double d2,
                long l3,
                double d3,
                long l4,
                double d4,
                long l5,
                double d5,
                long l6,
                double d6,
                long l7,
                double d7,
                long l8,
                double d8);
    }

    public static class CustomType {

    }

    private static final class BenchImpl implements BenchIFace {
        private final long tokens;

        public BenchImpl(final long tokens) {
            super();
            this.tokens = tokens;
        }

        @Override
        public void noArgs() {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void onePrimitiveArg(final int x) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void twoMixedLengthPrimitiveArgs(final int x, final long y) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void oneObjectArg(final Object x) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void oneReferenceArg(final CustomType x) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void tenMixedArgs(final int i,
                final Object o,
                final long l,
                final CustomType c0,
                final double d,
                final CustomType c1,
                final float f,
                final CustomType c2,
                final boolean b,
                final CustomType c3) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void unalignedPrimitiveArgs(
                long l1,
                double d1,
                long l2,
                double d2,
                long l3,
                double d3,
                long l4,
                double d4,
                long l5,
                double d5,
                long l6,
                double d6,
                long l7,
                double d7,
                long l8,
                double d8,
                int i) {
            Blackhole.consumeCPU(this.tokens);
        }

        @Override
        public void alignedPrimitiveArgs(int i,
                long l1,
                double d1,
                long l2,
                double d2,
                long l3,
                double d3,
                long l4,
                double d4,
                long l5,
                double d5,
                long l6,
                double d6,
                long l7,
                double d7,
                long l8,
                double d8) {
            Blackhole.consumeCPU(this.tokens);
        }

    }

    @AuxCounters
    @State(Scope.Thread)
    public static class ProcessorCounters {
        public long processed;
        public long processFailed;
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class CallerCounters {
        public long callsFailed;
    }

    public static final class StoppedException extends RuntimeException {

    }

    private static final StoppedException STOPPED = new StoppedException();

    private static final class MyWaitStrategy
            implements org.jctools.channels.WaitStrategy {
        public Control control;
        private int retries;

        @Override
        public int idle(final int idleCounter) {
            if (this.control.stopMeasurement) {
                throw STOPPED;
            }
            this.retries = idleCounter;
            return idleCounter + 1;
        }

    }

    private ProxyChannel<BenchIFace> mpscChannel;
    private BenchIFace proxy;
    private BenchIFace impl;
    private MyWaitStrategy waitStrategy;

    int intArg;
    Object objArg;
    long longArg;
    long longArg2;
    long longArg3;
    long longArg4;
    CustomType customType0;
    CustomType customType1;
    CustomType customType2;
    CustomType customType3;
    double doubleArg;
    double doubleArg2;
    double doubleArg3;
    double doubleArg4;
    float floatArg;
    boolean booleanArg;

    @Param({ "1", "" + CAPACITY })
    private int limit;

    @Setup(Level.Iteration)
    public void setupTrial() {
        this.waitStrategy = new MyWaitStrategy();
        this.mpscChannel = ProxyChannelFactory.createMpscProxy(CAPACITY, BenchIFace.class, this.waitStrategy);
        this.proxy = this.mpscChannel.proxy();
        this.impl = new BenchImpl(0);

        this.intArg = 7;
        this.objArg = new Object();
        this.longArg = System.nanoTime();
        this.longArg2 = System.nanoTime();
        this.longArg3 = System.nanoTime();
        this.longArg4 = System.nanoTime();
        this.customType0 = new CustomType();
        this.customType1 = new CustomType();
        this.customType2 = new CustomType();
        this.customType3 = new CustomType();
        this.doubleArg = System.nanoTime();
        this.doubleArg2 = System.nanoTime();
        this.doubleArg3 = System.nanoTime();
        this.doubleArg4 = System.nanoTime();
        this.floatArg = 8.165f;
        this.booleanArg = true;
    }

    @Benchmark
    public int oneObjectArgBaseline() {
        this.impl.oneObjectArg(this.objArg);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("oneObjectArg")
    @GroupThreads(PRODUCER_THREADS)
    public boolean oneObjectArgCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.oneObjectArg(this.objArg);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("oneObjectArg")
    @GroupThreads(CONSUMER_THREADS)
    public int oneObjectArgProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int oneReferenceArgBaseline() {
        this.impl.oneReferenceArg(this.customType0);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("oneReferenceArg")
    @GroupThreads(PRODUCER_THREADS)
    public boolean oneReferenceArgCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.oneReferenceArg(this.customType0);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("oneReferenceArg")
    @GroupThreads(CONSUMER_THREADS)
    public int oneReferenceArgProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int twoMixedLengthPrimitiveArgsBaseline() {
        this.impl.twoMixedLengthPrimitiveArgs(this.intArg, this.longArg);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("twoMixedLengthPrimitiveArgs")
    @GroupThreads(PRODUCER_THREADS)
    public boolean twoMixedLengthPrimitiveArgsCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.twoMixedLengthPrimitiveArgs(this.intArg, this.longArg);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("twoMixedLengthPrimitiveArgs")
    @GroupThreads(CONSUMER_THREADS)
    public int twoMixedLengthPrimitiveArgsProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int onePrimitiveArgBaseline() {
        this.impl.onePrimitiveArg(this.intArg);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("onePrimitiveArg")
    @GroupThreads(PRODUCER_THREADS)
    public boolean onePrimitiveArgCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.onePrimitiveArg(this.intArg);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("onePrimitiveArg")
    @GroupThreads(CONSUMER_THREADS)
    public int onePrimitiveArgProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int noArgsBaseline() {
        this.impl.noArgs();
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("noArgs")
    @GroupThreads(PRODUCER_THREADS)
    public boolean noArgsCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.noArgs();
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("noArgs")
    @GroupThreads(CONSUMER_THREADS)
    public int noArgsProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int tenMixedArgsBaseline() {
        this.impl.tenMixedArgs(this.intArg,
                this.objArg,
                this.longArg,
                this.customType0,
                this.doubleArg,
                this.customType1,
                this.floatArg,
                this.customType2,
                this.booleanArg,
                this.customType3);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("tenMixedArgs")
    @GroupThreads(PRODUCER_THREADS)
    public boolean tenMixedArgsCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.tenMixedArgs(this.intArg,
                    this.objArg,
                    this.longArg,
                    this.customType0,
                    this.doubleArg,
                    this.customType1,
                    this.floatArg,
                    this.customType2,
                    this.booleanArg,
                    this.customType3);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("tenMixedArgs")
    @GroupThreads(CONSUMER_THREADS)
    public int tenMixedArgsProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int alignedPrimitiveArgsBaseline() {
        this.impl.alignedPrimitiveArgs(intArg,
                longArg,
                doubleArg,
                longArg2,
                doubleArg2,
                longArg3,
                doubleArg3,
                longArg4,
                doubleArg4,
                longArg,
                doubleArg,
                longArg2,
                doubleArg2,
                longArg3,
                doubleArg3,
                longArg4,
                doubleArg4);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("alignedPrimitiveArgs")
    @GroupThreads(PRODUCER_THREADS)
    public boolean alignedPrimitiveArgsCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.alignedPrimitiveArgs(intArg,
                    longArg,
                    doubleArg,
                    longArg2,
                    doubleArg2,
                    longArg3,
                    doubleArg3,
                    longArg4,
                    doubleArg4,
                    longArg,
                    doubleArg,
                    longArg2,
                    doubleArg2,
                    longArg3,
                    doubleArg3,
                    longArg4,
                    doubleArg4);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("alignedPrimitiveArgs")
    @GroupThreads(CONSUMER_THREADS)
    public int alignedPrimitiveArgsProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    @Benchmark
    public int unalignedPrimitiveArgsBaseline() {
        this.impl.unalignedPrimitiveArgs(
                longArg,
                doubleArg,
                longArg2,
                doubleArg2,
                longArg3,
                doubleArg3,
                longArg4,
                doubleArg4,
                longArg,
                doubleArg,
                longArg2,
                doubleArg2,
                longArg3,
                doubleArg3,
                longArg4,
                doubleArg4,
                intArg);
        return this.waitStrategy.retries;
    }

    @Benchmark
    @Group("unalignedPrimitiveArgs")
    @GroupThreads(PRODUCER_THREADS)
    public boolean unalignedPrimitiveArgsCaller(final Control control, final CallerCounters counters) {
        this.waitStrategy.control = control;
        try {
            this.proxy.unalignedPrimitiveArgs(
                    longArg,
                    doubleArg,
                    longArg2,
                    doubleArg2,
                    longArg3,
                    doubleArg3,
                    longArg4,
                    doubleArg4,
                    longArg,
                    doubleArg,
                    longArg2,
                    doubleArg2,
                    longArg3,
                    doubleArg3,
                    longArg4,
                    doubleArg4,
                    intArg);
            counters.callsFailed = this.waitStrategy.retries;
            return true;
        } catch (final StoppedException e) {
            return false;
        }
    }

    @Benchmark
    @Group("unalignedPrimitiveArgs")
    @GroupThreads(CONSUMER_THREADS)
    public int unalignedPrimitiveArgsProcessor(final ProcessorCounters counters) {
        return doProcess(mpscChannel, counters);
    }

    private int doProcess(ProxyChannel<BenchIFace> proxyChannel, final ProcessorCounters counters) {
        final int processed = proxyChannel.process(this.impl, this.limit);
        if (processed == 0) {
            counters.processFailed++;
        } else {
            counters.processed += processed;
        }
        return processed;
    }

    public static void main(final String[] args) throws Exception {
//        final String logFile = SpscProxyChannelBenchmark.class.getSimpleName() + ".log";
        final Options opt = new OptionsBuilder()
                .include(MpscProxyChannelBenchmark.class.getSimpleName() + ".*tenMixedArgs.*")
                // .jvmArgsAppend("-XX:+UnlockDiagnosticVMOptions",
                // "-XX:+TraceClassLoading",
                // "-XX:+LogCompilation",
                // "-XX:LogFile=" + logFile,
                // "-XX:+PrintAssembly")
                .warmupIterations(5)
                .measurementIterations(5)
                .param("limit", "1")
                .forks(2)
                .build();
        new Runner(opt).run();
    }
}
