package org.jctools.queues;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

@JCStressTest
@Outcome(id = "1", expect = ACCEPTABLE, desc = "All ok.")
@Outcome(expect = FORBIDDEN)
@State
public class SpscArrayQueueProducerConsumerTest {
    private final SpscArrayQueue<Integer> queue = new SpscArrayQueue<>(3);

    public SpscArrayQueueProducerConsumerTest() {
        queue.offer(1);
    }

    @Actor
    public void actor1() {
        queue.poll();
    }

    @Actor
    public void actor2() {
        queue.offer(8);
    }

    @Arbiter
    public void arbiter(I_Result r) {
        r.r1 = queue.size();
    }
}
