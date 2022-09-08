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
package org.jctools.queues.intrusive;

import org.jctools.util.QueueFactory;
import org.jctools.util.AtomicQueueFactory;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;
import org.jctools.util.Pow2;
import org.junit.Before;
import org.junit.Test;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

public class IntrusiveQueueSanityTest {

    static final int SIZE = 8192 * 2;

    TestNode[] nodes = new TestNode[SIZE];

    private final MpscIntrusiveLinkedQueue queue = new MpscIntrusiveLinkedQueue();
    private final ConcurrentQueueSpec spec = new ConcurrentQueueSpec(0, 1, 0, Ordering.FIFO, Preference.NONE);


    @Before
    public void clear() {
        for (int i = 0; i < SIZE; i++) {
            nodes[i] = new TestNode();
            nodes[i].value = i;
        }
        queue.clear();
    }

    @Test
    public void sanity() {
        for (int i = 0; i < SIZE; i++) {
            assertNull(queue.poll());
            assertTrue(queue.isEmpty());
            assertEquals(0, queue.size());
        }
        int i = 0;
        while (i < SIZE && queue.offer(nodes[i])) i++;
        int size = i;
        assertEquals(size, queue.size());
        if (spec.ordering == Ordering.FIFO) {
            // expect FIFO
            i = 0;
            Node p;
            TestNode e;
            while ((p = queue.peek()) != null) {
                e = (TestNode) queue.poll();
                assertEquals(p, e);
                assertEquals(size - (i + 1), queue.size());
                assertEquals(i++, e.value);
            }
            assertEquals(size, i);
        } else {
            // expect sum of elements is (size - 1) * size / 2 = 0 + 1 + .... + (size - 1)
            int sum = (size - 1) * size / 2;
            TestNode e;
            while ((e = (TestNode) queue.poll()) != null) {
                assertEquals(--size, queue.size());
                sum -= e.value;
            }
            assertEquals(0, sum);
        }
    }

    @Test
    public void testSizeIsTheNumberOfOffers() {
        int currentSize = 0;
        while (currentSize < SIZE && queue.offer(nodes[currentSize])) {
            currentSize++;
            assertEquals(currentSize, queue.size());
        }
    }

    @Test
    public void whenFirstInThenFirstOut() {
        assumeThat(spec.ordering, is(Ordering.FIFO));

        // Arrange
        for (int i = 0; i < SIZE; i++) {
            nodes[i].value = i;
            queue.offer(nodes[i]);
        }
        final int size = queue.size();

        // Act
        int i = 0;
        Node prev;
        while ((prev = queue.peek()) != null) {
            final TestNode item = (TestNode) queue.poll();

            assertThat(item, is(prev));
            assertEquals(size - (i + 1), queue.size());
            assertThat(item.value, is(i));
            i++;
        }

        // Assert
        assertThat(i, is(size));
    }

    @Test(expected=NullPointerException.class)
    public void offerNullResultsInNPE(){
        queue.offer(null);
    }

    @Test
    public void whenOfferItemAndPollItemThenSameInstanceReturnedAndQueueIsEmpty() {
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());

        // Act
        final Integer e = 1876876;
        nodes[0].value = e;
        queue.offer(nodes[0]);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        TestNode retNode = (TestNode) queue.poll();
        final Integer oh = retNode.value;
        assertEquals(e, oh);
        assertSame(nodes[0], retNode);

        // Assert
        assertThat(retNode, sameInstance(nodes[0]));
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    public void testPowerOf2Capacity() {
        assumeThat(spec.isBounded(), is(true));
        int n = Pow2.roundToPowerOfTwo(spec.capacity);

        for (int i = 0; i < n; i++) {
            assertTrue("Failed to insert:" + i, queue.offer(nodes[i]));
        }
        assertFalse(queue.offer(new TestNode()));
        fail();
    }

    static final class Val {
        public int value;
    }

    @Test
    public void testHappensBefore() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MpscIntrusiveLinkedQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 1; i <= 10; i++) {
                        TestNode v = new TestNode();
                        v.value = i;
                        q.offer(v);
                    }
                    // slow down the producer, this will make the queue mostly empty encouraging visibility issues.
                    Thread.yield();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    for (int i = 0; i < 10; i++) {
                        TestNode v = (TestNode) q.peek();
                        if (v != null && v.value == 0) {
                            fail.value = 1;
                            stop.set(true);
                            System.out.println("v = " + v);
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

    @Test
    public void testSize() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean();
        final MpscIntrusiveLinkedQueue q = queue;
        final Val fail = new Val();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    q.offer(nodes[0]);
                    q.poll();
                }
            }
        });
        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop.get()) {
                    int size = q.size();
                    if(size != 0 && size != 1) {
                        fail.value = size;
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
        assertEquals("Unexpected size observed", 0, fail.value);
    }

    public static Object[] makeQueue(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q) {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE);
        if(q == null) {
            q = QueueFactory.newQueue(spec);
        }
        return new Object[] { spec, q };
    }
    public static Object[] makeAtomic(int producers, int consumers, int capacity, Ordering ordering, Queue<Integer> q) {
        ConcurrentQueueSpec spec = new ConcurrentQueueSpec(producers, consumers, capacity, ordering,
                Preference.NONE);
        if(q == null) {
            q = AtomicQueueFactory.newAtomicQueue(spec);
        }
        return new Object[] { spec, q };
    }

}
