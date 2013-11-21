package io.jaq;

import java.util.concurrent.ConcurrentLinkedQueue;

public class QueueFactory {
    public class QueueSpec{
        
    }
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
