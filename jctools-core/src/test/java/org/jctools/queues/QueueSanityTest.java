package org.jctools.queues;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.Queue;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

@RunWith(Parameterized.class)
public class QueueSanityTest {

    private static final int SIZE = 8192 * 2;
    private final static ConcurrentQueueSpec GROW = new ConcurrentQueueSpec(1, 1, SIZE, Ordering.FIFO, Preference.NONE);
    private final static ConcurrentQueueSpec UNBOUND = new ConcurrentQueueSpec(1, 1, 0, Ordering.FIFO, Preference.NONE);

    @Parameterized.Parameters
    public static Collection queues() {
        return Arrays.asList(test(1, 1, 1, Ordering.FIFO), test(1, 1, 0, Ordering.FIFO),
                test(1, 1, SIZE, Ordering.FIFO), test(1, 0, 1, Ordering.FIFO),
                test(1, 0, SIZE, Ordering.FIFO), test(0, 1, 0, Ordering.FIFO), test(0, 1, 1, Ordering.FIFO),
                test(0, 1, SIZE, Ordering.FIFO), test(0, 1, 1, Ordering.PRODUCER_FIFO),
                test(0, 1, SIZE, Ordering.PRODUCER_FIFO), test(0, 1, 1, Ordering.NONE),
                test(0, 1, SIZE, Ordering.NONE), test(0, 0, 1, Ordering.FIFO),
                test(0, 0, SIZE, Ordering.FIFO), new Object[] { GROW }, new Object[] { UNBOUND });
    }

    private final Queue<Integer> queue;
    private final ConcurrentQueueSpec spec;

    public QueueSanityTest(ConcurrentQueueSpec spec) {
        if (spec == GROW) {
            queue = new SpscGrowableArrayQueue<Integer>(SIZE);
        }
        else if (spec == UNBOUND) {
            queue = new SpscUnboundedArrayQueue<Integer>(128);
        }
        else {
            queue = QueueFactory.newQueue(spec);
        }
        this.spec = spec;
    }

    @Before
    public void clear() {
        queue.clear();
    }

    @Test
    public void sanity() {
        for (int i = 0; i < SIZE; i++) {
            assertNull(queue.poll());
            assertThat(queue, emptyAndZeroSize());
        }
        int i = 0;
        while (i < SIZE && queue.offer(i))
            i++;
        int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            i = 0;
            Integer p;
            Integer e;
            while ((p = queue.peek()) != null) {
                e = queue.poll();
                assertEquals(p, e);
                assertEquals(size - (i + 1), queue.size());
                assertEquals(i++, e.intValue());
            }
            assertEquals(size, i);
        } else {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            i = 0;
            Integer e;
            while ((e = queue.poll()) != null) {
                assertEquals(--size, queue.size());
                sum -= e.intValue();
            }
            assertEquals(0, sum);
        }
    }

    @Test
    public void testSizeIsTheNumberOfOffers() {
        int currentSize = 0;
        while (currentSize < SIZE && queue.offer(currentSize)) {
            currentSize++;
            assertThat(queue, hasSize(currentSize));
        }
    }

    @Test
    public void whenFirstInThenFirstOut() {
        assumeThat(spec.ordering, is(Ordering.FIFO));

        // Arrange
        int i = 0;
        while (i < SIZE && queue.offer(i)) {
            i++;
        }
        final int size = queue.size();

        // Act
        i = 0;
        Integer prev;
        while ((prev = queue.peek()) != null) {
            final Integer item = queue.poll();

            assertThat(item, is(prev));
            assertThat(queue, hasSize(size - (i + 1)));
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
        while (i < SIZE && queue.offer(i)) {
            i++;
        }
        int size = queue.size();

        // Act
        // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
        int sum = (size - 1) * size / 2;
        Integer e;
        while ((e = queue.poll()) != null) {
            size--;
            assertThat(queue, hasSize(size));
            sum -= e;
        }

        // Assert
        assertThat(sum, is(0));
    }

    @Test
    public void whenOfferItemAndPollItemThenSameInstanceReturnedAndQueueIsEmpty() {
        assertThat(queue, emptyAndZeroSize());

        // Act
        final Integer e = 1;
        queue.offer(e);
        assertThat(queue, not(emptyAndZeroSize()));
        assertThat(queue, hasSize(1));

        final Integer oh = queue.poll();

        // Assert
        assertThat(oh, sameInstance(e));
        assertThat(queue, emptyAndZeroSize());
    }

    private static Object[] test(int producers, int consumers, int capacity, Ordering ordering) {
        return new Object[] { new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE) };
    }

}
