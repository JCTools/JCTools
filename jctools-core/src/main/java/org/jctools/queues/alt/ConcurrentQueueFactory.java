package org.jctools.queues.alt;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.jctools.queues.MpmcConcurrentQueue;
import org.jctools.queues.MpscCompoundQueue;
import org.jctools.queues.MpscConcurrentQueue;
import org.jctools.queues.SpmcConcurrentQueue;
import org.jctools.queues.SpscLinkedQueue;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Growth;
import org.jctools.queues.spec.Ordering;

/**
 * The queue factory produces {@link ConcurrentQueue} instances based on a best fit to the
 * {@link ConcurrentQueueSpec}. This allows minimal dependencies between user code and the queue
 * implementations and gives users a way to express their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class ConcurrentQueueFactory {
    public static <E> ConcurrentQueue<E> newQueue(ConcurrentQueueSpec qs) {
        if (qs.growth == Growth.BOUNDED) {
            // SPSC
            if (qs.consumers == 1 && qs.producers == 1) {

                return new FFBufferWithOfferBatchCq<>(qs.capacity);
            }
            // MPSC
            else if (qs.consumers == 1) {
                if (qs.ordering != Ordering.NONE) {
                    return new MpscConcurrentQueue<>(qs.capacity);
                } else {
                    return new MpscCompoundQueue<>(qs.capacity);
                }
            }
            // SPMC
            else if (qs.producers == 1) {
                return new SpmcConcurrentQueue<>(qs.capacity);
            }
            // MPMC
            else {
                return new MpmcConcurrentQueue<>(qs.capacity);
            }
        }
        else {
            if (qs.consumers == 1 && qs.producers == 1) {

                return new SpscLinkedQueue<>();
            }

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
