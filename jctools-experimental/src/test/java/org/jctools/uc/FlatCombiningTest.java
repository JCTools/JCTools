package org.jctools.uc;

import org.jctools.uc.FlatCombining.Operation;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class FlatCombiningTest {
    @Test
    public void stressTest() throws Exception {
        int count = 100000;
        List<Integer> ints = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ints.add(i + 1);
        }
        Collections.shuffle(ints);

        Spliterator<Integer> setA = ints.spliterator();
        Spliterator<Integer> setB = setA.trySplit();
        Spliterator<Integer> setC = setA.trySplit();
        Spliterator<Integer> setD = setB.trySplit();

        assertNotEquals(-1, setA.getExactSizeIfKnown());
        assertEquals(setA.getExactSizeIfKnown(), setB.getExactSizeIfKnown());
        assertEquals(setA.getExactSizeIfKnown(), setC.getExactSizeIfKnown());
        assertEquals(setA.getExactSizeIfKnown(), setD.getExactSizeIfKnown());

        PriorityQueue<Integer> pq = new PriorityQueue<Integer>(count);
        FlatCombining<PriorityQueue<Integer>> fc = new FlatCombining<PriorityQueue<Integer>>(pq);
        CountDownLatch startLatch = new CountDownLatch(1);

        Thread a = new Thread(new QueueAppender(startLatch, setA, fc));
        Thread b = new Thread(new QueueAppender(startLatch, setB, fc));
        Thread c = new Thread(new QueueAppender(startLatch, setC, fc));
        Thread d = new Thread(new QueueAppender(startLatch, setD, fc));

        a.start();
        b.start();
        c.start();
        d.start();
        d.join(10);

        startLatch.countDown();

        a.join();
        b.join();
        c.join();
        d.join();

        assertEquals(pq.size(), count);
        int curr = 0;
        Integer x;
        while ((x = pq.poll()) != null) {
            assertEquals(curr + 1, (int) x);
            curr = x;
        }
    }

    private static class QueueAppender implements Runnable, Consumer<Integer> {
        private final CountDownLatch startLatch;
        private final Spliterator<Integer> spliterator;
        private final FlatCombining<PriorityQueue<Integer>> fc;

        public QueueAppender(CountDownLatch startLatch, Spliterator<Integer> spliterator,
                             FlatCombining<PriorityQueue<Integer>> fc) {
            this.startLatch = startLatch;
            this.spliterator = spliterator;
            this.fc = fc;
        }

        @Override
        public void run() {
            spliterator.forEachRemaining(this);
        }

        @Override
        public void accept(Integer x) {
            try {
                startLatch.await();
                fc.apply(new PriorityQueueAddOperation(x));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class PriorityQueueAddOperation
            implements Operation<PriorityQueue<Integer>, Boolean, RuntimeException> {
        private final Integer x;

        public PriorityQueueAddOperation(Integer x) {
            this.x = x;
        }

        @Override
        public Boolean apply(PriorityQueue<Integer> pq) throws RuntimeException {
            return pq.add(x);
        }
    }
}