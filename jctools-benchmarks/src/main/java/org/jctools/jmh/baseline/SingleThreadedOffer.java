package org.jctools.jmh.baseline;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
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
    public static final int OPS = 1 << 15;
    public static final Integer TOKEN = 1;
   
    Queue<Integer> q = QueueByTypeFactory.createQueue(OPS*2);
    volatile boolean preventUnrolling = true;
    @Setup(Level.Invocation)
    public void clear()
    {
        q.clear();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void offerLoop()
    {
        final Queue<Integer> lq = q;
        for (int i = 0; i < OPS && preventUnrolling; i++)
        {
            lq.offer(TOKEN);
        }
    }
}
