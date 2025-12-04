package org.jctools.varhandle;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.varhandle.padding.PaddedThread;

/**
 * A Multiple-Producer, Single-Consumer (MPSC) bounded lock-free queue using a circular array and
 * VarHandles. It adds blocking capabilities for a single consumer (take, timed offer).
 *
 * <p>All operations are wait-free for the consumer and lock-free for producers.
 *
 * @param <E> the type of elements stored
 */
class MpscBlockingConsumerArrayQueueVarHandle<E> extends MpscArrayQueueVarHandle<E>
    implements MessagePassingQueue<E>, BlockingQueue<E> {

  /** Reference to the waiting consumer thread (set atomically). */
  private final PaddedThread consumerThread = new PaddedThread();

  /**
   * Creates a new MPSC queue.
   *
   * @param requestedCapacity the desired capacity, rounded up to next power of two
   */
  public MpscBlockingConsumerArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
  }

  @Override
  public final boolean offer(E e) {
    Objects.requireNonNull(e);

    // jctools does the same local copy to have the jitter optimise the accesses
    final Object[] localBuffer = this.buffer;

    long localProducerLimit = producerLimit.getVolatile();
    long cachedHead = 0L; // Local cache of head to reduce volatile reads

    int spinCycles = 0;
    boolean parkOnSpin = (Thread.currentThread().getId() & 1) == 0;

    while (true) {
      long currentTail = tail.getVolatile();

      // Check if producer limit exceeded
      if (currentTail >= localProducerLimit) {
        // Refresh head only when necessary
        cachedHead = head.getVolatile();
        localProducerLimit = cachedHead + capacity;

        if (currentTail >= localProducerLimit) {
          return false; // queue full
        }

        // Update producerLimit so other producers also benefit
        producerLimit.setVolatile(localProducerLimit);
      }

      // Attempt to claim a slot
      if (tail.compareAndSet(currentTail, currentTail + 1)) {
        final int index = arrayIndex(currentTail);

        // Release-store ensures producer's write is visible to consumer
        ARRAY_HANDLE.setRelease(localBuffer, index, e);

        // Atomically clear and unpark the consumer if waiting
        Thread c = consumerThread.getAndSet(null);
        if (c != null) {
          LockSupport.unpark(c);
        }

        return true;
      }

      // Backoff to reduce contention
      if ((spinCycles & 1) == 0) {
        Thread.onSpinWait();
      } else {
        if (parkOnSpin) {
          LockSupport.parkNanos(1);
        } else {
          Thread.yield();
        }
      }
      spinCycles++;
    }
  }

  @Override
  public void put(E e) throws InterruptedException {
    if (!offer(e)) {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
    {
      if (offer(e)) {
        return true;
      }
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public final E poll(long timeout, TimeUnit unit) throws InterruptedException {
    E e = poll();
    if (e != null) {
      return e;
    }

    final long parkNanos = unit.toNanos(timeout);
    if (parkNanos <= 0) {
      return null;
    }

    parkUntilNext(parkNanos);

    return poll();
  }

  @Override
  public int remainingCapacity() {
    return capacity - size();
  }

  @Override
  public int drainTo(Collection<? super E> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int drainTo(Collection<? super E> c, int maxElements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public E take() throws InterruptedException {
    consumerThread.setVolatile(Thread.currentThread());
    E e;
    while ((e = poll()) == null) {
      parkUntilNext(-1);
    }
    return e;
  }

  /**
   * Blocks (parks) until an element becomes available or until the specified timeout elapses.
   *
   * <p>It is safe if only one thread is waiting (it's the case for this single consumer
   * implementation).
   *
   * @param nanos max wait time in nanoseconds. If negative, it will park indefinably until waken or
   *     interrupted
   * @throws InterruptedException if interrupted while waiting
   */
  private void parkUntilNext(long nanos) throws InterruptedException {
    Thread current = Thread.currentThread();
    // Publish the consumer thread (no ordering required)
    consumerThread.setOpaque(current);
    if (nanos <= 0) {
      LockSupport.park(this);
    } else {
      LockSupport.parkNanos(this, nanos);
    }

    if (Thread.interrupted()) {
      throw new InterruptedException();
    }

    // Cleanup (no fence needed, single consumer)
    consumerThread.setOpaque(null);
  }
}
