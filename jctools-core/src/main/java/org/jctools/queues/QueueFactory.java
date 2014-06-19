package org.jctools.queues;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Growth;
import org.jctools.queues.spec.Ordering;

/**
 * The queue factory produces {@link java.util.Queue} instances based on a best fit to the
 * {@link ConcurrentQueueSpec}. This allows minimal dependencies between user code and the queue
 * implementations and gives users a way to express their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class QueueFactory {
    public static <E> Queue<E> newQueue(ConcurrentQueueSpec qs) {
        if (qs.growth == Growth.BOUNDED) {
            // SPSC
            if (qs.consumers == 1 && qs.producers == 1) {

                return new SpscArrayQueue<>(qs.capacity);
            }
            // MPSC
            else if (qs.consumers == 1) {
                if (qs.ordering != Ordering.NONE) {
                    return new MpscArrayQueue<E>(qs.capacity);
                } else {
                    return new MpscCompoundQueue<E>(qs.capacity);
                }
            }
            // SPMC
            else if (qs.producers == 1) {
                return new SpmcArrayQueue<E>(qs.capacity);
            }
            // MPMC
            else {
                return new MpmcArrayQueue<E>(qs.capacity);
            }
        } else {
            if (qs.consumers == 1 && qs.producers == 1) {
                return new SpscLinkedQueue<E>();
            }
        }
        return new ConcurrentLinkedQueue<E>();
    }
}
