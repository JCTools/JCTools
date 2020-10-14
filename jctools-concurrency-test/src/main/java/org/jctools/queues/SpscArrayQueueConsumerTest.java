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
public class SpscArrayQueueConsumerTest {
    private final SpscArrayQueue<Integer> queue = new SpscArrayQueue<>(3);

    public SpscArrayQueueConsumerTest() {
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
    }

    @Actor
    public void actor1() {
        queue.poll(); queue.poll();
    }

    @Arbiter
    public void arbiter(I_Result r) {
        r.r1 = queue.size();
    }
}
