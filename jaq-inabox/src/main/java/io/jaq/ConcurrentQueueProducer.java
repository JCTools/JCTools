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
package io.jaq;

/**
 * @author nitsanw
 *
 */
public interface ConcurrentQueueProducer<E> {
    /**
     * @param e
     * @return true if e was successfully offered, false if queue is full
     */
    boolean offer(E e);
    
    /**
     * No atomicity guarantees are offered, so may be implemented simply as a loop over offer call.
     * 
     * @param ea an array of elements to be offered as a batch
     * @param count number of elements to offer from the array, must be less than ea.length
     * @return true if ea was successfully offered, false if queue is full
     */
    boolean offer(E[] ea);
    
    /**
     * This allows batching of events directly onto the queue by delaying the flush trigger.
     *  
     * @param e
     * @param flush
     * @return 
     */
//    boolean offer(E e, boolean flush);
}