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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class MpscArrayQueueSnapshotTest {

    private MpscArrayQueue<Integer> queue;

    @Before
    public void setUp() throws Exception {
        this.queue = new MpscArrayQueue<>(4);
    }

    @Test
    public void testIterator() {
        queue.offer(0);
        assertThat(iteratorToList(), contains(0));
        for (int i = 1; i < queue.capacity(); i++) {
            queue.offer(i);
        }
        assertThat(iteratorToList(), containsInAnyOrder(0, 1, 2, 3));
        queue.poll();
        queue.offer(4);
        queue.poll();
        assertThat(iteratorToList(), containsInAnyOrder(2, 3, 4));
    }
    
    @Test
    public void testIteratorHasNextConcurrentModification() {
        //There may be gaps in the elements returned by the iterator,
        //but hasNext needs to be reliable even if the elements are consumed between hasNext() and next().
        queue.offer(0);
        queue.offer(1);
        Iterator<Integer> iter = queue.iterator();
        assertThat(iter.hasNext(), is(true));
        queue.poll();
        queue.poll();
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
