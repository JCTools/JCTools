package org.jctools.queues.atomic;

import org.junit.Test;

import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class ScLinkedAtomicQueueTest {
    protected abstract Queue<Integer> newQueue();

    private static <T> void assertQueueEmpty(Queue<T> queue) {
        assertNull(queue.peek());
        assertNull(queue.poll());
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    private void removeSimple(int removeValue, int expectedFirst, int expectedSecond) throws InterruptedException {
        final Queue<Integer> queue = newQueue();
        Thread t = new Thread() {
            @Override
            public void run() {
                queue.offer(1);
                queue.offer(2);
                queue.offer(3);
            }
        };

        assertQueueEmpty(queue);

        t.start();

        while (queue.size() < 3) {
            Thread.yield();
        }

        assertTrue(queue.remove(removeValue));
        // Try to remove again, just to ensure pointers are updated as expected.
        assertFalse(queue.remove(removeValue));
        assertFalse(queue.isEmpty());
        assertEquals(2, queue.size());

        assertEquals(expectedFirst, queue.poll().intValue());
        assertEquals(expectedSecond, queue.poll().intValue());
        assertQueueEmpty(queue);

        t.join();
    }

    @Test
    public void removeConsumerNode() throws InterruptedException {
        removeSimple(1, 2, 3);
    }

    @Test
    public void removeInteriorNode() throws InterruptedException {
        removeSimple(2, 1, 3);
    }

    @Test
    public void removeProducerNode() throws InterruptedException {
        removeSimple(3, 1, 2);
    }

    @Test
    public void removeFailsWhenExpected() throws InterruptedException {
        final Queue<Integer> queue = newQueue();
        Thread t = new Thread() {
            @Override
            public void run() {
                queue.offer(1);
                queue.offer(2);
                queue.offer(3);
            }
        };

        assertQueueEmpty(queue);

        t.start();

        while (queue.size() < 3) {
            Thread.yield();
        }

        // Remove an element which doesn't exist.
        assertFalse(queue.remove(4));
        assertFalse(queue.remove(4));
        assertFalse(queue.isEmpty());
        assertEquals(3, queue.size());

        // Verify that none of the links have been modified.
        assertEquals(1, queue.poll().intValue());
        assertEquals(2, queue.poll().intValue());
        assertEquals(3, queue.poll().intValue());
        assertQueueEmpty(queue);

        t.join();
    }

    @Test
    public void removeStressTest() throws InterruptedException {
        // The test maybe racy ... just repeat it numerous times to increase the likely hood we will catch a failure.
        for (int i = 0; i < 10000; ++i) {
            final Queue<Integer> queue = newQueue();
            Thread t = new Thread() {
                @Override
                public void run() {
                    queue.offer(1);
                    queue.offer(2);
                    queue.offer(3);
                    queue.offer(4);
                    queue.offer(5);
                    queue.offer(6);
                }
            };

            assertQueueEmpty(queue);

            t.start();

            while (queue.size() < 3) {
                Thread.yield();
            }

            // Remove from the middle
            assertTrue(queue.remove(2));
            assertFalse(queue.remove(2));

            // Remove from the front
            assertTrue(queue.remove(1));
            assertFalse(queue.remove(1));

            assertTrue(queue.remove(3));
            assertFalse(queue.remove(3));

            while (queue.size() != 3) {
                Thread.yield();
            }

            // Remove from the end
            assertTrue(queue.remove(6));
            assertFalse(queue.remove(6));

            // Remove from the middle
            assertTrue(queue.remove(4));
            assertFalse(queue.remove(4));

            // Remove the last element
            assertTrue(queue.remove(5));
            assertFalse(queue.remove(5));

            assertQueueEmpty(queue);

            t.join();
        }
    }
}
