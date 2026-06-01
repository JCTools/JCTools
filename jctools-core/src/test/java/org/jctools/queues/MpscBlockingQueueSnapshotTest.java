/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jctools.queues;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

public class MpscBlockingQueueSnapshotTest {

    private MpscBlockingQueue<Integer> queue;

    @Before
    public void setUp() throws Exception {
        this.queue = new MpscBlockingQueue<>(8);
    }

    @Test
    public void testOffer() {
        assertThat(queue.offer(1), is(true));
        assertThat(queue.offer(2), is(true));
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testPoll() {
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);

        assertThat(queue.poll(), is(1));
        assertThat(queue.poll(), is(2));
        assertThat(queue.poll(), is(3));
        assertThat(queue.poll(), nullValue());
    }

    @Test
    public void testPollWithTimeout() throws InterruptedException {
        queue.offer(1);

        assertThat(queue.poll(100, TimeUnit.MILLISECONDS), is(1));
        assertThat(queue.poll(100, TimeUnit.MILLISECONDS), nullValue());
    }

    @Test
    public void testPeek() {
        queue.offer(1);
        queue.offer(2);

        assertThat(queue.peek(), is(1));
        assertThat(queue.peek(), is(1));
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testPeekEmpty() {
        assertThat(queue.peek(), nullValue());
    }

    @Test
    public void testSize() {
        assertThat(queue.size(), is(0));
        queue.offer(1);
        assertThat(queue.size(), is(1));
        queue.offer(2);
        queue.offer(3);
        assertThat(queue.size(), is(3));
        queue.poll();
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(queue.isEmpty());
        queue.offer(1);
        assertThat(queue.isEmpty(), is(false));
        queue.poll();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void testDrainTo() {
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);

        List<Integer> drained = new ArrayList<>();
        int count = queue.drainTo(drained);

        assertThat(count, is(3));
        assertThat(drained.size(), is(3));
        assertThat(queue.isEmpty(), is(true));
    }

    @Test
    public void testDrainToWithLimit() {
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
        queue.offer(4);

        List<Integer> drained = new ArrayList<>();
        int count = queue.drainTo(drained, 2);

        assertThat(count, is(2));
        assertThat(drained.size(), is(2));
        assertThat(queue.size(), is(2));
    }

    @Test
    public void testOfferToFullQueue() {
        queue.offer(1);
        queue.offer(2);
        queue.offer(3);
        queue.offer(4);
        queue.offer(5);
        queue.offer(6);
        queue.offer(7);
        queue.offer(8);

        assertThat(queue.offer(9), is(false));
        assertThat(queue.size(), is(8));
    }

    @Test
    public void testMpscOrdering() throws InterruptedException {
        Thread producer1 = new Thread(() -> {
            queue.offer(1);
            queue.offer(2);
        });
        Thread producer2 = new Thread(() -> {
            queue.offer(10);
            queue.offer(20);
        });

        producer1.start();
        producer2.start();
        producer1.join();
        producer2.join();

        List<Integer> values = new ArrayList<>();
        Integer val;
        while ((val = queue.poll()) != null) {
            values.add(val);
        }

        assertThat(values.size(), is(4));
        // Verify ordering per producer: 1 before 2, and 10 before 20
        int idx1 = values.indexOf(1);
        int idx2 = values.indexOf(2);
        int idx10 = values.indexOf(10);
        int idx20 = values.indexOf(20);

        assertTrue("Value 1 should come before 2", idx1 < idx2);
        assertTrue("Value 10 should come before 20", idx10 < idx20);
    }

    @Test
    public void testConcurrentPutAndPoll() throws InterruptedException {
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    queue.put(i);
                    Thread.sleep(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        List<Integer> values = new ArrayList<>();
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    Integer val = queue.poll(500, TimeUnit.MILLISECONDS);
                    if (val != null) {
                        values.add(val);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        assertThat(values.size(), is(10));
        for (int i = 0; i < 10; i++) {
            assertThat(values.get(i), is(i));
        }
    }

    @Test
    public void testPut() throws InterruptedException
    {
        queue.put(1);
        queue.put(2);
        queue.put(3);

        assertThat(queue.take(), is(1));
        assertThat(queue.take(), is(2));
        assertThat(queue.take(), is(3));
    }

    @Test
    public void testTake() throws InterruptedException
    {
        queue.put(100);
        queue.put(200);

        assertThat(queue.take(), is(100));
        assertThat(queue.take(), is(200));
    }

    @Test(timeout = 5000)
    public void testAwaitEmpty() throws InterruptedException
    {
        System.out.println("Before final awaitEmpty, isEmpty: " + queue.isEmpty());
        queue.awaitEmpty();
        System.out.println("Final awaitEmpty returned");
    }

}
