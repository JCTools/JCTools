package io.jaq;

public interface QProducer<E> {
    boolean offer(E e);
}