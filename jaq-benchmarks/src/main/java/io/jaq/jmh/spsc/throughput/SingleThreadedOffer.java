package io.jaq.jmh.spsc.throughput;

import io.jaq.spsc.SPSCQueueFactory;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({Mode.AverageTime})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class SingleThreadedOffer
{
    public final Queue<Integer> q = SPSCQueueFactory.createQueue();
    @Setup(Level.Invocation)
    public void fill()
    {
        for( int i=0; i<SPSCQueueFactory.CAPACITY; i++)
        {
            q.offer(Integer.MIN_VALUE);
        }
    }

    @GenerateMicroBenchmark
    @OperationsPerInvocation(SPSCQueueFactory.CAPACITY)
    public void poll()
    {
        for (int i = 0; i < SPSCQueueFactory.CAPACITY; i++)
        {
            q.poll();
        }
    }
}
