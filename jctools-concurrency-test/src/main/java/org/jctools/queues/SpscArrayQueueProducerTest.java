package org.jctools.queues;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.ZZ_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

@JCStressTest
@Outcome(id = "true, true", expect = ACCEPTABLE, desc = "All ok.")
@Outcome(id = "true, false", expect = ACCEPTABLE_INTERESTING, desc = "Size is broken.")
@Outcome(id = "false, true", expect = ACCEPTABLE_INTERESTING, desc = "Offer broken.")
@Outcome(id = "false, false", expect = FORBIDDEN, desc = "Nothing ran.")
@State
public class SpscArrayQueueProducerTest {
    private final SpscArrayQueue<Integer> queue = new SpscArrayQueue<>(3);

    @Actor
    public void actor1(ZZ_Result r) {
        r.r1 = queue.offer(1);
    }

    @Arbiter
    public void arbiter1(ZZ_Result r) {
        r.r2 = !queue.isEmpty();
    }
}
