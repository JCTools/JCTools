package org.jctools.queues;

import java.util.Queue;

/**
 * This is a tagging interface for the queues in this library which implement a subset of the {@link Queue} interface
 * sufficient for concurrent message passing.<br>
 * Message passing queues offer happens before semantics to messages passed through, namely that writes made by the
 * producer before offering the message are visible to the consuming thread after the message has been polled out of the
 * queue.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public interface MessagePassingQueue<M> {
    /**
     * Called from a producer thread subject to the restrictions appropriate to the implementation and according to the
     * {@link Queue#offer(Object)} interface.
     * 
     * @param message
     * @return true if element was inserted into the queue, false if queue is full
     */
    boolean offer(M message);

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and according to
     * the {@link Queue#poll()} interface.
     * 
     * @return null if empty, or a message from the queue if one is available
     */
    M poll();

    /**
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and according to
     * the {@link Queue#peek()} interface.
     * 
     * @return null if empty, or a message from the queue if one is available
     */
    M peek();

    /**
     * This method's accuracy is subject to concurrent modifications happening as the size is estimated and as such is a
     * best effort rather than absolute value.
     * 
     * @return number of messages in the queue
     */
    int size();

}
