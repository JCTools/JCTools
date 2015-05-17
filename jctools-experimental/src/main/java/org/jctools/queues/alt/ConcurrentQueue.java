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

/**
 * A minimal top level queue interface which allows producer/consumers access via separate interfaces.
 * 
 * @author nitsanw
 * 
 * @param <E> element type
 */
public interface ConcurrentQueue<E> {
    /**
     * @return a consumer instance to be used from a consuming thread.
     */
    ConcurrentQueueConsumer<E> consumer();

    /**
     * @return a producer instance to be used from a producing thread.
     */
    ConcurrentQueueProducer<E> producer();

    /**
     * This method may be O(n) or O(1) and may not be accurate.
     * 
     * @return the number of elements in the queue
     */
    int size();

    /**
     * @return the maximum number of elements that can fit in this queue, or -1 if unbounded.
     */
    int capacity();
}
