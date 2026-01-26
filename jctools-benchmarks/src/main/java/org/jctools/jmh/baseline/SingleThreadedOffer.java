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
   
    volatile boolean preventUnrolling = true;
    @Param(value = { "SpscArrayQueue", "MpscArrayQueue", "SpmcArrayQueue", "MpmcArrayQueue" })
    String qType;
    Queue<Integer> q;
    
    @Setup(Level.Trial)
    public void createQ() {
        q = QueueByTypeFactory.createQueue(qType, OPS*2);
    }

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
            blackhole(queueOffer(lq, TOKEN));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public boolean queueOffer(Queue<Integer> lq, Integer e)
    {
        return lq.offer(e);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean v) {}
}
