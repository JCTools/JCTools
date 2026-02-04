package org.jctools.jmh.baseline;

import org.openjdk.jmh.annotations.*;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode({Mode.AverageTime})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class SingleThreadedBaseline
{
    public static final int OPS = 1 << 15;
    public static final Integer TOKEN = 1;
   
    volatile boolean preventUnrolling = true;
    boolean dummyOfferReturn = true;
    Integer dummyPollReturn = TOKEN;
    Queue<Integer> q = new ArrayDeque<>();
    @Benchmark
    @OperationsPerInvocation(OPS)
    public void baselineOffer()
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
        return dummyOfferReturn;
    }
    
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(boolean v) {}

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void baselinePoll()
    {
        final Queue<Integer> lq = q;
        for (int i = 0; i < OPS && preventUnrolling; i++)
        {
            blackhole(queuePoll(lq));
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public Integer queuePoll(Queue<Integer> lq)
    {
        return dummyPollReturn;
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void blackhole(Object e) {}
}
