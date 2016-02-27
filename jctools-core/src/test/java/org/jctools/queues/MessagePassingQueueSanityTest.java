package org.jctools.queues;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.util.Pow2;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MessagePassingQueueSanityTest {

    private static final int SIZE = 8192 * 2;

    @Parameterized.Parameters
    public static Collection parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(test(1, 1, 0, Ordering.FIFO, null));// unbounded SPSC
        list.add(test(0, 1, 0, Ordering.FIFO, null));// unbounded MPSC

        list.add(test(1, 1, 4, Ordering.FIFO, null));// SPSC size 4
        list.add(test(1, 1, SIZE, Ordering.FIFO, null));// SPSC size SIZE

        list.add(test(1, 0, 1, Ordering.FIFO, null));// SPMC size 1
        list.add(test(1, 0, SIZE, Ordering.FIFO, null));// SPMC size SIZE

        list.add(test(0, 1, 1, Ordering.FIFO, null));// MPSC size 1
        list.add(test(0, 1, SIZE, Ordering.FIFO, null));// MPSC size SIZE

        list.add(test(0, 1, 4, Ordering.FIFO, new MpscGrowableArrayQueue<>(4)));// MPSC size 1
        list.add(test(0, 1, SIZE, Ordering.FIFO, new MpscGrowableArrayQueue<>(2, SIZE)));// MPSC size SIZE

        list.add(test(0, 0, 2, Ordering.FIFO, null));
        list.add(test(0, 0, SIZE, Ordering.FIFO, null));
        return list;
    }

    private final MessagePassingQueue<Integer> queue;
    private final ConcurrentQueueSpec spec;

    public MessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
        this.queue = queue;
        this.spec = spec;
    }

    @Before
    public void clear() {
        queue.clear();
    }

    @Test
    public void sanity() {
        for (int i = 0; i < SIZE; i++) {
            assertNull(queue.relaxedPoll());
            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
        }
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i))
            i++;
        int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            i = 0;
            Integer p;
            Integer e;
            while ((p = queue.relaxedPeek()) != null) {
                e = queue.relaxedPoll();
                assertEquals(p, e);
                assertEquals(size - (i + 1), queue.size());
                assertEquals(i++, e.intValue());
            }
            assertEquals(size, i);
        }
        else {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            i = 0;
            Integer e;
            while ((e = queue.relaxedPoll()) != null) {
                assertEquals(--size, queue.size());
                sum -= e;
            }
            assertEquals(0, sum);
        }
    }

    int count = 0;
    Integer p;

    @Test
    public void sanityDrainBatch() {
        assertEquals(0, queue.drain(e -> {
        } , SIZE));
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);
        count = 0;
        int i = queue.fill(() -> {
            return count++;
        } , SIZE);
        final int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            p = queue.relaxedPeek();
            count = 0;
            i = queue.drain(e -> {
                // batch consumption can cause size to differ from following expectation
                // this is because elements are 'claimed' in a batch and their consumption lags
                if (spec.consumers == 1) {
                    assertEquals(p, e); // peek will return the post claim peek
                    assertEquals(size - (count + 1), queue.size()); // size will return the post claim size
                }
                assertEquals(count++, e.intValue());
                p = queue.relaxedPeek();
            });
            p = null;
            assertEquals(size, i);
            assertTrue(queue.isEmpty());
            assertTrue(queue.size() == 0);
        }
        else {
        }
    }

    @Test
    public void testSizeIsTheNumberOfOffers() {
        int currentSize = 0;
        while (currentSize < SIZE && queue.relaxedOffer(currentSize)) {
            currentSize++;
            assertFalse(queue.isEmpty());
            assertTrue(queue.size() == currentSize);
        }
    }

    @Test
    public void whenFirstInThenFirstOut() {
        assumeThat(spec.ordering, is(Ordering.FIFO));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i)) {
            i++;
        }
        final int size = queue.size();

        // Act
        i = 0;
        Integer prev;
        while ((prev = queue.relaxedPeek()) != null) {
            final Integer item = queue.relaxedPoll();

            assertThat(item, is(prev));
            assertEquals((size - (i + 1)), queue.size());
            assertThat(item, is(i));
            i++;
        }

        // Assert
        assertThat(i, is(size));
    }

    @Test
    public void test_FIFO_PRODUCER_Ordering() throws Exception {
        assumeThat(spec.ordering, is(not((Ordering.FIFO))));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.relaxedOffer(i)) {
            i++;
        }
        int size = queue.size();

        // Act
        // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
        int sum = (size - 1) * size / 2;
        Integer e;
        while ((e = queue.relaxedPoll()) != null) {
            size--;
            assertEquals(size, queue.size());
            sum -= e;
        }

        // Assert
        assertThat(sum, is(0));
    }

    @Test
    public void whenOfferItemAndPollItemThenSameInstanceReturnedAndQueueIsEmpty() {
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);

        // Act
        final Integer e = 1;
        queue.relaxedOffer(e);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        final Integer oh = queue.relaxedPoll();

        // Assert
        assertTrue(queue.isEmpty());
        assertTrue(queue.size() == 0);
    }

    @Test
    public void testPowerOf2Capacity() {
        assumeThat(spec.isBounded(), is(true));
        int n = Pow2.roundToPowerOfTwo(spec.capacity);

        for (int i = 0; i < n; i++) {
            assertTrue("Failed to insert:" + i, queue.relaxedOffer(i));
        }
        assertFalse(queue.relaxedOffer(n));
    }

    static final class Val {
        public int value;
    }

    @Test
    public void testHappensBefore() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 1; i <= 10; i++) {
                        Val v = new Val();
                        v.value = i;
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 0; i < 10; i++) {
                        Val v = (Val) q.relaxedPeek();
                        if (v != null && v.value == 0) {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.relaxedPoll();
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualDrain() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            int counter;

            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 1; i <= 10; i++) {
                        Val v = new Val();
                        v.value = i;
                        q.relaxedOffer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    q.drain(e -> {
                        Val v = (Val) e;
                        if (v != null && v.value == 0) {
                            fail.value = 1;
                            stop.set(true);
                        }
                    } , e -> {
                        return e;
                    } , () -> {
                        return !stop.get();
                    });
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualFill() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            int counter;

            @Override
            public void run() {
                counter = 1;
                q.fill(() -> {
                    Val v = new Val();
                    v.value = 1 + (counter++ % 10);
                    return v;
                } , e -> {
                    return e;
                } , () -> {
                    // slow down the producer, this will make the queue mostly empty encouraging visibility
                    // issues.
                    Thread.yield();
                    return !stop.get();
                });
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 0; i < 10; i++) {
                        Val v = (Val) q.relaxedPeek();
                        int r;
                        if (v != null && (r = v.value) == 0) {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.relaxedPoll();
                    }
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    @Test
    public void testHappensBeforePrepetualFillDrain() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MessagePassingQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            int counter;

            @Override
            public void run() {
                counter = 1;
                q.fill(() -> {
                    Val v = new Val();
                    v.value = 1 + (counter++ % 10);
                    return v;
                } , e -> {
                    return e;
                } , () -> { // slow down the producer, this will make the queue mostly empty encouraging
                            // visibility issues.
                    Thread.yield();
                    return !stop.get();
                });
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    q.drain(e -> {
                        Val v = (Val) e;
                        if (v != null && v.value == 0) {
                            fail.value = 1;
                            stop.set(true);
                        }
                    } , e -> {
                        return e;
                    } , () -> {
                        return !stop.get();
                    });
                }
            }
        });

        t1.start();
        t2.start();
        Thread.sleep(1000);
        stop.set(true);
        t1.join();
        t2.join();
        assertEquals("reordering detected", 0, fail.value);

    }

    private static Object[] test(int producers, int consumers, int capacity, Ordering ordering,
            Queue<Integer> q) {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE);
        if (q == null) {
            q = QueueFactory.newQueue(spec);
        }
        return new Object[] { spec, q };
    }

}
