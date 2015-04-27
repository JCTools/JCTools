/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues.atomic;

import static org.hamcrest.Matchers.*;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.junit.Assert.*;
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
public class AtomicQueueSanityTest {

    private static final int SIZE = 8192 * 2;
    @Parameterized.Parameters
    public static Collection<?> parameters() {
        ArrayList<Object[]> list = new ArrayList<Object[]>();
        list.add(test(1, 1, 1, Ordering.FIFO, null));
        list.add(test(1, 1, 0, Ordering.FIFO, null));
        list.add(test(1, 1, SIZE, Ordering.FIFO, null));
//        list.add(test(1, 1, 16, Ordering.FIFO, new SpscGrowableArrayQueue<Integer>(4, 16)));
        list.add(test(1, 1, 0, Ordering.FIFO, new SpscUnboundedAtomicArrayQueue<Integer>(16)));
        list.add(test(1, 0, 1, Ordering.FIFO, null));
        list.add(test(1, 0, SIZE, Ordering.FIFO, null));
        list.add(test(0, 1, 0, Ordering.FIFO, null));
        list.add(test(0, 1, 1, Ordering.FIFO, null));
        list.add(test(0, 1, SIZE, Ordering.FIFO, null));
        list.add(test(0, 1, 1, Ordering.PRODUCER_FIFO, null));
        list.add(test(0, 1, SIZE, Ordering.PRODUCER_FIFO, null));
        // Compound queue minimal size is the core count
        list.add(test(0, 1, Runtime.getRuntime().availableProcessors(), Ordering.NONE, null));
        list.add(test(0, 1, SIZE, Ordering.NONE, null));
        // Mpmc minimal size is 2
        list.add(test(0, 0, 2, Ordering.FIFO, null));
        list.add(test(0, 0, SIZE, Ordering.FIFO, null));
        return list;
    }

    private final Queue<Integer> queue;
    private final ConcurrentQueueSpec spec;

    public AtomicQueueSanityTest(ConcurrentQueueSpec spec, Queue<Integer> queue) {
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

    @Test
    public void testPowerOf2Capacity() {
        assumeThat(spec.isBounded(), is(true));
        int n = Pow2.roundToPowerOfTwo(spec.capacity);

        for (int i = 0; i < n; i++) {
            assertTrue("Failed to insert:"+i,queue.offer(i));
        }
        assertFalse(queue.offer(n));
    }

    static final class Val {
        public int value;
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void testHappensBefore() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final Queue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 1; i <= 10; i++) {
                        Val v = new Val();
                        v.value = i;
                        q.offer(v);
                    }
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 0; i < 10; i++) {
                        Val v = (Val) q.peek();
                        if (v != null && v.value == 0) {
                            fail.value = 1;
                            stop.set(true);
                        }
                        q.poll();
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

    private static Object[] test(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q) {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE);
        if(q == null) {
            q = AtomicQueueFactory.newQueue(spec);
        }
        return new Object[] { spec, q };
    }

}
