package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;

public interface TakeStrategy<E>
{
    void signal();
    E waitPoll(Queue<E> q) throws InterruptedException;
    boolean supportsSpec(ConcurrentQueueSpec qs);
}
