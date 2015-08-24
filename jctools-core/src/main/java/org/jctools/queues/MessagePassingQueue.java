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

import java.util.Queue;

/**
 * This is a tagging interface for the queues in this library which implement a subset of the {@link Queue} interface
 * sufficient for concurrent message passing.<br>
 * Message passing queues provide happens before semantics to messages passed through, namely that writes made by the
 * producer before offering the message are visible to the consuming thread after the message has been polled out of the
 * queue.
 * 
 * @author nitsanw
 * 
 * @param <M> the event/message type
 */
public interface MessagePassingQueue<M> {
    int UNBOUNDED_CAPACITY = -1;
    interface Supplier<M> {
        M get();
    }
    interface Consumer<M> {
        void accept(M m);
    }
    
    /**
     * Called from a producer thread subject to the restrictions appropriate to the implementation and according to the
     * {@link Queue#offer(Object)} interface.
     * 
     * @param message
     * @return true if element was inserted into the queue, false iff full
     */
    boolean offer(M message);

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and according to
     * the {@link Queue#poll()} interface.
     * 
     * @return a message from the queue if one is available, null iff empty
     */
    M poll();

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and according to
     * the {@link Queue#peek()} interface.
     * 
     * @return a message from the queue if one is available, null iff empty
     */
    M peek();

    /**
     * This method's accuracy is subject to concurrent modifications happening as the size is estimated and as such is a
     * best effort rather than absolute value. For some implementations this method may be O(n) rather than O(1).
     * 
     * @return number of messages in the queue, between 0 and {@link Integer#MAX_VALUE} but less or equals to capacity (if bounded).
     */
    int size();

    /**
     * Removes all items from the queue. Called from the consumer thread subject to the restrictions appropriate to the implementation and according to
     * the {@link Queue#clear()} interface.
     */
    void clear();
    
    
    /**
     * This method's accuracy is subject to concurrent modifications happening as the observation is carried out.
     * 
     * @return true if empty, false otherwise
     */
    boolean isEmpty();
    
    /**
     * @return the capacity of this queue or UNBOUNDED_CAPACITY if not bounded
     */
    int capacity();
    
    /**
     * Called from a producer thread subject to the restrictions appropriate to the implementation. As opposed to 
     * {@link Queue#offer(Object)} this method may return false without the queue being full.
     * 
     * @param message
     * @return true if element was inserted into the queue, false if unable to offer
     */
    boolean relaxedOffer(M message);

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As opposed to 
     * {@link Queue#poll()} this method may return null without the queue being empty.
     * 
     * @return a message from the queue if one is available, null if unable to poll
     */
    M relaxedPoll();

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As opposed to 
     * {@link Queue#peek()} this method may return null without the queue being empty.
     * 
     * @return a message from the queue if one is available, null if unable to peek
     */
    M relaxedPeek();
    
    
    /**
     * Remove all available item from the queue and hand to consume. This should be semantically similar to:
     * <code><br/>
     * M m;</br>
     * while((m = relaxedPoll()) != null){</br>
     *  c.accept(m);</br>
     * }</br>
     * </code>
     * There's no strong commitment to the queue being empty at the end of a drain.
     * Called from a consumer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of drained elements
     */
//    int drain(Consumer<M> c);
    
    /**
     * Stuff the queue with elements from the supplier. Semantically similar to:
     * <code><br/>
     * while(relaxedOffer(s.get());</br>
     * </code>
     * There's no strong commitment to the queue being full at the end of a fill.
     * Called from a producer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of queued elements
     */
//    int fill(Supplier<M> s);
    /**
     * Remove up to <i>limit</i> elements from the queue and hand to consume. This should be semantically similar to:
     * <code><br/>
     * M m;</br>
     * while((m = relaxedPoll()) != null){</br>
     *  c.accept(m);</br>
     * }</br>
     * </code>
     * There's no strong commitment to the queue being empty at the end of a drain.
     * Called from a consumer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of drained elements
     */
//    int drain(Consumer<M> c, int limit);
    
    /**
     * Stuff the queue with up to <i>limit</i> elements from the supplier. Semantically similar to:
     * <code><br/>
     * for(int i=0; i < limit && relaxedOffer(s.get(); i++);</br>
     * </code>
     * There's no strong commitment to the queue being full at the end of a fill.
     * Called from a producer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of queued elements
     */
//    int fill(Supplier<M> s, int limit);
    
    /**
     * Remove all available item from the queue and hand to consume. This should be semantically similar to:
     * <code><br/>
     * M m;</br>
     * while((m = relaxedPoll()) != null){</br>
     *  c.accept(m);</br>
     * }</br>
     * </code>
     * There's no strong commitment to the queue being empty at the end of a drain.
     * Called from a consumer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of drained elements
     */
//    int drain(Consumer<M> c);
    
    /**
     * Stuff the queue with elements from the supplier. Semantically similar to:
     * <code><br/>
     * while(relaxedOffer(s.get());</br>
     * </code>
     * There's no strong commitment to the queue being full at the end of a fill.
     * Called from a producer thread subject to the restrictions appropriate to the implementation.
     * 
     * @return the number of queued elements
     */
//    int fill(Supplier<M> s);
}
