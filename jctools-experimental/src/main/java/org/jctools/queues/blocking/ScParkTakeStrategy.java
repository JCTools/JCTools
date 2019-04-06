package org.jctools.queues.blocking;

import org.jctools.queues.spec.ConcurrentQueueSpec;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

public final class ScParkTakeStrategy<E> implements TakeStrategy<E> {

    private static final AtomicReferenceFieldUpdater<ScParkTakeStrategy, Thread> WAITING_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(ScParkTakeStrategy.class, Thread.class, "waiting");

    public volatile int storeFence = 0;

    private volatile Thread waiting = null;

    @Override
    public void signal()
    {
        // Make sure the offer is visible before unpark
        storeFence = 1; // store load barrier

        LockSupport.unpark(waiting); // t.get() load barrier
    }

    @Override
    public E waitPoll(Queue<E> q) throws InterruptedException {
        E e = q.poll();
        if (e != null) {
            return e;
        }

        Thread currentThread = Thread.currentThread();
        waiting = currentThread;

        while ((e = q.poll()) == null) {
            LockSupport.park();

            if (currentThread.isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for the queue to become non-empty.");
            }
        }

        WAITING_UPDATER.lazySet(this, null);

        return e;
    }

    @Override
    public boolean supportsSpec(ConcurrentQueueSpec qs) {
        return qs.consumers == 1;
    }

}
