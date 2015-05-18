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
package org.jctools.queues.alt;

import java.util.Queue;
import java.util.function.Consumer;

/**
 * Consumers are local to the threads which use them. A thread should therefore call {@link ConcurrentQueue#consumer()}
 * to obtain an instance and should only use it's own instance to access the queue.
 * 
 * @author nitsanw
 * 
 */
public interface ConcurrentQueueConsumer<E> {

    /**
     * As many elements as are visible are delivered to the {@link Consumer}.
     * 
     * @param batchSize this is the limit on the batch consume operation, but it is possible that less are available
     * @return number of elements consumed
     */
    int consume(ConsumerFunction<E> consumer, int batchSize);

    /**
     * See {@link Queue#poll()} for contract.
     * @return next element or null if queue is empty
     */
    E poll();
    
    /**
     * Remove the next element from the queue and return it.
     * 
     * @return next element or null if next element is not available (queue may not be empty)
     */
    E weakPoll();
    
    /**
     * See {@link Queue#peek()} for contract.
     * 
     * @return next element or null if queue is empty
     */
    E peek();

    /**
     * Return the next element from the queue, but don't remove it.
     * 
     * @return next element or null if next element is not available (queue may not be empty)
     */
    E weakPeek();
    
    /**
     * Remove all elements from the queue. This will not stop the producers from adding new elements, so only guarantees
     * elements visible to the consumer on first sweep are removed.
     */
    void clear();
}