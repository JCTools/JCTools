package org.jctools.jmh.baseline;

import org.jctools.queues.QueueByTypeFactory;
import org.openjdk.jmh.annotations.*;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

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
