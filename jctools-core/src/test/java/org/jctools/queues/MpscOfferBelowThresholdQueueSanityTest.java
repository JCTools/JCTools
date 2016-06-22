package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MpscOfferBelowThresholdQueueSanityTest extends QueueSanityTest {
    /**
     * This allows us to test the offersIfBelowThreshold through all the offer utilizing threads. The effect should be
     * as if the queue capacity is halved.
     */
    static class MpscArrayQueueOverride<E> extends MpscArrayQueue<E> {
        int threshold;
        public MpscArrayQueueOverride(int capacity) {
            super(capacity);
            threshold = capacity()/2;
        }
        @Override
        public boolean offer(E e) {
            return super.offerIfBelowThreshold(e, threshold);
        }
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(makeQueue(0, 1, 8, Ordering.FIFO, new MpscArrayQueueOverride<Integer>(16)));
        return list;
    }

    public MpscOfferBelowThresholdQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
        super(spec, queue);
    }

}
