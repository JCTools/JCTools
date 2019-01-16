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
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;

public class MpscArrayQueueSnapshotTest {

    private MpscArrayQueue<Integer> queue;

    @Before
    public void setUp() throws Exception {
        this.queue = new MpscArrayQueue<>(4);
    }

    @Test
    public void testSnapshot() {
        queue.offer(0);
        assertThat(queue.unorderedSnapshot(), contains(0));
        for (int i = 1; i < queue.capacity(); i++) {
            queue.offer(i);
        }
        assertThat(queue.unorderedSnapshot(), containsInAnyOrder(0, 1, 2, 3));
        queue.poll();
        queue.offer(4);
        assertThat(queue.unorderedSnapshot(), containsInAnyOrder(1, 2, 3, 4));
    }
    
}
