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

import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.jctools.queues.matchers.Matchers.emptyAndZeroSize;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class SpscAtomicArrayQueueTest
{
    @Test
    public void shouldWorkAfterWrap()
    {
        // Arrange
        final SpscAtomicArrayQueue<Object> q = new SpscAtomicArrayQueue<Object>(1024);
        // starting point for empty queue at max long, next offer will wrap the producerIndex
        q.soConsumerIndex(Long.MAX_VALUE);
        q.soProducerIndex(Long.MAX_VALUE);
        q.producerLimit = Long.MAX_VALUE;
        // valid starting point
        assertThat(q, emptyAndZeroSize());

        // Act
        // assert offer is successful
        final Object e = new Object();
        assertTrue(q.offer(e));
        // size is computed correctly after wrap
        assertThat(q, not(emptyAndZeroSize()));
        assertThat(q, hasSize(1));

        // now consumer index wraps
        final Object poll = q.poll();
        assertThat(poll, sameInstance(e));
        assertThat(q, emptyAndZeroSize());

        // let's go again
        assertTrue(q.offer(e));
        assertThat(q, not(emptyAndZeroSize()));

        final Object poll2 = q.poll();
        assertThat(poll2, sameInstance(e));
        assertThat(q, emptyAndZeroSize());
    }
}
