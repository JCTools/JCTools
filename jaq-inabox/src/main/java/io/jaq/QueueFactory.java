package io.jaq;

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
    public static <E> AQueue<E> newQueue(QueueSpec qs){
        return new GenericQueue<E>();
    }
    // generic queue solution to fill gaps for now
    private final static class GenericQueue<E> extends ConcurrentLinkedQueue<E> implements AQueue<E>, QConsumer<E>, QProducer<E>{
        private static final long serialVersionUID = -599236378503873292L;

        @Override
        public QConsumer<E> consumer(int index) {
            return this;
        }

        @Override
        public QProducer<E> producer(int index) {
            return this;
        }
        
    }
}
