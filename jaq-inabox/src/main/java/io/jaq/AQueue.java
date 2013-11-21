package io.jaq;


public interface AQueue<E> {
    QConsumer<E> consumer(int index);
//    void process(Processor<E> p, Executor e);
    QProducer<E> producer(int index);
//    QBatchProducer<E> batchProducer();
}
