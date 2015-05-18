package org.jctools.queues.alt;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.concurrent.ConcurrentLinkedQueue;

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

                return new SpscArrayConcurrentQueue<E>(qs.capacity);
            }
            else {
                return new MpmcArrayConcurrentQueue<E>(qs.capacity);
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

        @Override
        public int produce(ProducerFunction<E> producer, int batchSize) {
            E e;
            int i=0;
            for(;i<batchSize;i++) {
                e = producer.produce();
                assert e != null;
                weakOffer(e);
            }
            return i;
        }
        
        @Override
        public int consume(ConsumerFunction<E> consumer, int batchSize) {
            E e;
            int i=0;
            for(;i<batchSize;i++) {
                if((e = weakPoll()) == null){
                    break;
                }
                consumer.consume(e);
            }
            return i;
        }
        
        @Override
        public boolean weakOffer(E e) {
            return offer(e);
        }
        
        @Override
        public E weakPoll() {
            return poll();
        }

        @Override
        public E weakPeek() {
            return peek();
        }
    }
}
