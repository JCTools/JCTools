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

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MpscUnboundedArrayQueueSnapshotTest {

    private static final int CHUNK_SIZE = 4;
    private MpscUnboundedArrayQueue<Integer> queue;

    @Before
    public void setUp() throws Exception {
        this.queue = new MpscUnboundedArrayQueue<>(CHUNK_SIZE + 1); //Account for extra slot for JUMP
    }

    @Test
    public void testIterator() {
        queue.offer(0);
        assertThat(iteratorToList(), contains(0));
        for (int i = 1; i < CHUNK_SIZE; i++) {
            queue.offer(i);
        }
        assertThat(iteratorToList(), containsInAnyOrder(0, 1, 2, 3));
        queue.offer(4);
        queue.offer(5);
        assertThat(iteratorToList(), containsInAnyOrder(0, 1, 2, 3, 4, 5));
        queue.poll();
        assertThat(iteratorToList(), containsInAnyOrder(1, 2, 3, 4, 5));
        for (int i = 1; i < CHUNK_SIZE; i++) {
            queue.poll();
        }
        assertThat(iteratorToList(), containsInAnyOrder(4, 5));
    }

    @Test
    public void testIteratorOutpacedByConsumer() {
        int slotsToForceMultipleBuffers = CHUNK_SIZE + 1;
        for (int i = 0; i < slotsToForceMultipleBuffers; i++) {
            queue.offer(i);
        }
        Iterator<Integer> iter = queue.iterator();
        List<Integer> entries = new ArrayList<>();
        entries.add(iter.next());
        for (int i = 0; i < CHUNK_SIZE; i++) {
            queue.poll();
        }
        //Now that the consumer has discarded the first buffer, the iterator needs to follow it to the new buffer.
        iter.forEachRemaining(entries::add);
        assertThat(entries, containsInAnyOrder(0, 1, 4));
    }

    @Test
    public void testIteratorHasNextConcurrentModification() {
        /*
         * There may be gaps in the elements returned by the iterator, but hasNext needs to be reliable even if the elements are consumed
         * between hasNext() and next(), and even if the consumer buffer changes.
         */
        int slotsToForceMultipleBuffers = CHUNK_SIZE + 1;
        for (int i = 0; i < slotsToForceMultipleBuffers; i++) {
            queue.offer(i);
        }
        Iterator<Integer> iter = queue.iterator();
        assertThat(iter.hasNext(), is(true));
        for (int i = 0; i < slotsToForceMultipleBuffers; i++) {
            queue.poll();
        }
        assertThat(queue.isEmpty(), is(true));
        assertThat(iter.hasNext(), is(true));
        assertThat(iter.next(), is(0));
        assertThat(iter.hasNext(), is(false));
    }

    private List<Integer> iteratorToList() {
        List<Integer> list = new ArrayList<>();
        Iterator<Integer> iter = queue.iterator();
        iter.forEachRemaining(list::add);
        return list;
    }

}
