package io.jaq;

import io.jaq.spsc.FFBufferWithOfferBatch;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The queue factory produces {@link ConcurrentQueue} instances based on a best fit to the {@link ConcurrentQueueSpec}. This
 * allows minimal dependencies between user code and the queue implementations and gives users a way to
 * express their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class ConcurrentQueueFactory {
    public static <E> ConcurrentQueue<E> newQueue(ConcurrentQueueSpec qs) {
        if (qs.consumers == 1 && qs.producers == 1) {
            if (qs.growth == Growth.BOUNDED) {
                return new FFBufferWithOfferBatch<>(qs.capacity);
            }
        }
        return new GenericQueue<E>();
    }

    // generic queue solution to fill gaps for now
    private final static class GenericQueue<E> extends ConcurrentLinkedQueue<E> implements ConcurrentQueue<E>,
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
        public void consumeBatch(BatchConsumer<E> bc, int batchSizeLimit) {
            boolean last;
            do {
                E e = poll();
                if (e == null) {
                    return;
                }
                last = isEmpty() || --batchSizeLimit == 0;
                bc.consume(e, last);
            } while (!last);
        }

        @Override
        public boolean offer(E[] es) {
            for (E e : es) {
                offer(e);
            }
            return true;
        }
        @Override
        public int capacity() {
            return Integer.MAX_VALUE;
        }

    }
}
