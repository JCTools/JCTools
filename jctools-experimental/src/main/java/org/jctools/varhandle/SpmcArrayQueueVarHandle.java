package org.jctools.varhandle;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.varhandle.padding.PaddedLong;

/**
 * A Single-Producer, Multiple-Consumer (SPMC) bounded, lock-free queue based on a circular array.
 *
 * <p>All operations are wait-free for the single producer and lock-free for consumers.
 *
 * @param <E> the element type
 */
final class SpmcArrayQueueVarHandle<E> extends BaseQueue<E> implements MessagePassingQueue<E> {
  /** Cached consumer limit to avoid repeated volatile tail reads */
  private final PaddedLong consumerLimit = new PaddedLong();

  /**
   * Creates a new SPMC queue.
   *
   * @param requestedCapacity the desired capacity, rounded up to next power of two
   */
  public SpmcArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
  }

  @Override
  public boolean offer(E e) {
    Objects.requireNonNull(e);

    long currentTail = tail.getVolatile();
    long wrapPoint = currentTail - capacity;
    long currentHead = head.getVolatile();

    if (wrapPoint >= currentHead) {
      return false; // queue full
    }

    int index = arrayIndex(currentTail);

    // Release-store ensures that the element is visible to consumers
    ARRAY_HANDLE.setRelease(this.buffer, index, e);

    // Single-producer: simple volatile write to advance tail
    tail.setVolatile(currentTail + 1);
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public E poll() {
    final Object[] localBuffer = this.buffer;

    int spinCycles = 0;
    boolean parkOnSpin = (Thread.currentThread().getId() & 1) == 0;

    while (true) {
      long currentHead = head.getVolatile();
      long limit = consumerLimit.getVolatile(); // cached tail

      if (currentHead >= limit) {
        // refresh limit once from tail volatile
        limit = tail.getVolatile();
        if (currentHead >= limit) {
          return null; // queue empty
        }
        consumerLimit.setVolatile(limit); // update local cache
      }

      // Attempt to claim this slot
      if (!head.compareAndSet(currentHead, currentHead + 1)) {
        // CAS failed. Backoff to reduce contention
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
        continue;
      }

      int index = arrayIndex(currentHead);
      Object value;

      // Spin-wait until producer publishes
      while ((value = ARRAY_HANDLE.getAcquire(localBuffer, index)) == null) {
        Thread.onSpinWait();
      }

      // Clear slot for GC
      ARRAY_HANDLE.setOpaque(localBuffer, index, null);
      return (E) value;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public E peek() {
    long currentHead = head.getVolatile();
    long currentTail = tail.getVolatile();

    if (currentHead >= currentTail) return null;

    return (E) ARRAY_HANDLE.getAcquire(buffer, currentHead); // acquire-load ensures visibility
  }

  /** A relaxed version of {@link #offer(Object)}. */
  @Override
  public boolean relaxedOffer(E e) {
    Objects.requireNonNull(e);

    // may see stale head, acceptable for SPMC
    long currentTail = tail.getOpaque();
    long wrapPoint = currentTail - capacity;

    // stale is ok; worst case we reject earlier
    long currentHead = head.getOpaque();

    if (wrapPoint >= currentHead) {
      return false; // queue looks full from this relaxed view
    }

    int index = arrayIndex(currentTail);

    // MUST remain release to publish element safely
    ARRAY_HANDLE.setRelease(this.buffer, index, e);

    // tail update needs no ordering (single producer)
    tail.setOpaque(currentTail + 1);

    return true;
  }

  /** A relaxed version of {@link #poll()}. */
  @Override
  @SuppressWarnings("unchecked")
  public E relaxedPoll() {
    final Object[] localBuffer = this.buffer;

    while (true) {

      long currentHead = head.getOpaque();
      long limit = consumerLimit.getOpaque();

      if (currentHead >= limit) {
        limit = tail.getOpaque();

        if (currentHead >= limit) {
          return null; // looks empty
        }

        consumerLimit.setOpaque(limit);
      }

      // CAS enforces consumer/consumer order, remains required
      if (!head.compareAndSet(currentHead, currentHead + 1)) {
        Thread.onSpinWait();
        continue;
      }

      int index = arrayIndex(currentHead);

      // producer used release so visibility is still guaranteed
      Object value;
      while ((value = ARRAY_HANDLE.getOpaque(localBuffer, index)) == null) {
        Thread.onSpinWait(); // spin until producer publishes
      }

      ARRAY_HANDLE.setOpaque(localBuffer, index, null);

      return (E) value;
    }
  }

  /** A relaxed version of {@link #peek()}. */
  @Override
  @SuppressWarnings("unchecked")
  public E relaxedPeek() {
    long currentHead = head.getOpaque();
    long currentTail = tail.getOpaque();

    if (currentHead >= currentTail) {
      return null; // appears empty
    }

    int index = arrayIndex(currentHead);

    return (E) ARRAY_HANDLE.getOpaque(buffer, index);
  }
}
