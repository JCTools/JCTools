package io.jaq;

import io.jaq.spsc.FFBufferWithOfferBatch;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The queue factory produces {@link AQueue} instances based on a best fit to the {@link QueueSpec}. This
 * allows minimal dependencies between user code and the queue implementations and gives users a way to
 * express their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class QueueFactory {
    public static <E> AQueue<E> newQueue(QueueSpec qs) {
        if (qs.consumers == 1 && qs.producers == 1) {
            if (qs.growth == Growth.BOUNDED) {
                return new FFBufferWithOfferBatch<>(qs.capacity);
            }
        }
        return new GenericQueue<E>();
    }

    // generic queue solution to fill gaps for now
    private final static class GenericQueue<E> extends ConcurrentLinkedQueue<E> implements AQueue<E>,
            QConsumer<E>, QProducer<E> {
        private static final long serialVersionUID = -599236378503873292L;

        @Override
        public QConsumer<E> consumer() {
            return this;
        }

        @Override
        public QProducer<E> producer() {
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

    }
}
