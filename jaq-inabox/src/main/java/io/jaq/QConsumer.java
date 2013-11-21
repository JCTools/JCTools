package io.jaq;

public interface QConsumer<E> {
    E poll();
}