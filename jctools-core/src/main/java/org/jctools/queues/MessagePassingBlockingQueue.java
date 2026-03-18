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
package org.jctools.queues;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A {@link MessagePassingQueue} that also implements the {@link BlockingQueue}
 * contract, combining high-performance message-passing semantics with
 * blocking and timed waiting capabilities.
 *
 * @param <E> the type of elements held in this queue
 * @see MessagePassingQueue
 * @see BlockingQueue
 */
public interface MessagePassingBlockingQueue<E> extends MessagePassingQueue<E>, BlockingQueue<E>
{
    /**
     * Remove up to <i>limit</i> elements from the queue and hand to consume, waiting up to the specified wait time if
     * necessary for an element to become available.
     * <p>
     * There's no strong commitment to the queue being empty at the end of it.
     * This implementation is correct for single consumer thread use only.
     * <p>
     * <b>WARNING</b>: Explicit assumptions are made with regard to {@link Consumer#accept} make sure you have read
     * and understood these before using this method.
     *
     * @return the number of polled elements
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalArgumentException c is {@code null}
     * @throws IllegalArgumentException if limit is negative
     */
    int drain(Consumer<E> c, final int limit, long timeout, TimeUnit unit) throws InterruptedException;
}