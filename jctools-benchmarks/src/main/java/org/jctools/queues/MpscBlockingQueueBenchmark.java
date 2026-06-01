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
package org.jctools.queues;

import java.util.concurrent.TimeUnit;

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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmark for MpscBlockingQueue throughput with multiple producers
 * and a single consumer. Tests both blocking and non-blocking variants.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class MpscBlockingQueueBenchmark
{
    private static final int NUM_PRODUCERS = 8;

    @Param({"8", "64", "128", "512", "4096"})
    public int queueCapacity;

    private MpscBlockingQueue<Integer> queue;
    private volatile int producerValue = 0;

    @Setup(Level.Trial)
    public void setup()
    {
        queue = new MpscBlockingQueue<>(queueCapacity);
    }

    @TearDown(Level.Trial)
    public void tearDown()
    {
        queue.clear();
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class ProducerCounters
    {
        public long offersFailed = 0;
        public long offersMade = 0;
    }

    @AuxCounters
    @State(Scope.Thread)
    public static class ConsumerCounters
    {
        public long pollsFailed = 0;
        public long pollsMade = 0;
    }

    /**
     * Non-blocking offer/poll benchmark.
     */
    @Benchmark
    @Group("offerPoll")
    @GroupThreads(NUM_PRODUCERS)
    public void offerProducer(ProducerCounters counters)
    {
        if (queue.offer(producerValue++))
        {
            counters.offersMade++;
        }
        else
        {
            counters.offersFailed++;
        }
    }

    @Benchmark
    @Group("offerPoll")
    @GroupThreads(1)
    public void pollConsumer(ConsumerCounters counters)
    {
        Integer item = queue.poll();
        if (item != null)
        {
            counters.pollsMade++;
        }
        else
        {
            counters.pollsFailed++;
        }
    }

    /**
     * Timed poll benchmark.
     */
    @Benchmark
    @Group("timedPoll")
    @GroupThreads(NUM_PRODUCERS)
    public void offerProducerTimed(ProducerCounters counters)
    {
        if (queue.offer(producerValue++))
        {
            counters.offersMade++;
        }
        else
        {
            counters.offersFailed++;
        }
    }

    @Benchmark
    @Group("timedPoll")
    @GroupThreads(1)
    public void pollConsumerTimed(ConsumerCounters counters) throws InterruptedException
    {
        Integer item = queue.poll(100, TimeUnit.MILLISECONDS);
        if (item != null)
        {
            counters.pollsMade++;
        }
        else
        {
            counters.pollsFailed++;
        }
    }

    public static void main(String[] args) throws Exception
    {
        Options opt = new OptionsBuilder()
            .include(MpscBlockingQueueBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
