package org.jctools.queues.takestrategy;

import java.util.Queue;

public interface TakeStrategy<E>
{
    void signal();

    E waitFor(Queue<E> q) throws InterruptedException;
}
