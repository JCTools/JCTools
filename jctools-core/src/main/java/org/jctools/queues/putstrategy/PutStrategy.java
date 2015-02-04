package org.jctools.queues.putstrategy;

import java.util.Queue;

public interface PutStrategy<E>
{
    void backoffOffer(Queue<E> q, E e);
    void signal();
}
