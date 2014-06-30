package org.jctools.queues.alt;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.jctools.queues.spec.ConcurrentQueueSpec;

/**
 * The queue factory produces {@link ConcurrentQueue} instances based on a best fit to the {@link ConcurrentQueueSpec}.
 * This allows minimal dependencies between user code and the queue implementations and gives users a way to express
 * their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class ConcurrentQueueFactory {
    public static <E> ConcurrentQueue<E> newQueue(ConcurrentQueueSpec qs) {
        if (qs.isBounded()) {
            // SPSC
            if (qs.consumers == 1 && qs.producers == 1) {

                return new SpscArrayConcurrentQueue<>(qs.capacity);
            }
            // In flux, for now it is correct to return MPMC...
            // MPSC
            // else if (qs.consumers == 1) {
            // if (qs.ordering != Ordering.NONE) {
            // return new MpscArrayQueue<>(qs.capacity);
            // } else {
            // return new MpscCompoundQueue<>(qs.capacity);
            // }
            // }
            // // SPMC
            // else if (qs.producers == 1) {
            // return new SpmcArrayQueue<>(qs.capacity);
            // }
            // MPMC
            else {
                return new MpmcArrayConcurrentQueue<>(qs.capacity);
            }
        } else {
            // if (qs.consumers == 1 && qs.producers == 1) {
            // return new SpscLinkedQueue<>();
            // }
        }
        return new GenericQueue<E>();
    }

    // generic queue solution to fill gaps for now
    public final static class GenericQueue<E> extends ConcurrentLinkedQueue<E> implements ConcurrentQueue<E>,
            ConcurrentQueueConsumer<E>, ConcurrentQueueProducer<E> {
        private static final long serialVersionUID = -599236378503873292L;

        @Override
        public ConcurrentQueueConsumer<E> consumer() {
            return this;
        }

        @Override
        public ConcurrentQueueProducer<E> producer() {
            return this;
        }

        @Override
        public int capacity() {
            return Integer.MAX_VALUE;
        }

    }
}
