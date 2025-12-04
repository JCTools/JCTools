package org.jctools.varhandle;

import java.util.Objects;
import java.util.concurrent.locks.LockSupport;
import org.jctools.varhandle.padding.PaddedLong;

/**
 * A Multiple-Producer, Single-Consumer (MPSC) bounded lock-free queue using a circular array and
 * VarHandles.
 *
 * <p>All operations are wait-free for the consumer and lock-free for producers.
 *
 * @param <E> the type of elements stored
 */
public class MpscArrayQueueVarHandle<E> extends BaseQueue<E> {
  /** Cached producer limit to reduce volatile head reads */
  protected final PaddedLong producerLimit;

  /**
   * Creates a new MPSC queue.
   *
   * @param requestedCapacity the desired capacity, rounded up to next power of two
   */
  public MpscArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
    this.producerLimit = new PaddedLong(capacity);
    ;
  }

  @Override
  public boolean offer(E e) {
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
  @SuppressWarnings("unchecked")
  public final E poll() {
    final Object[] localBuffer = this.buffer;

    long currentHead = head.getOpaque();
    final int index = arrayIndex(currentHead);

    // Acquire-load ensures visibility of producer write
    Object value = ARRAY_HANDLE.getAcquire(localBuffer, index);
    if (value == null) {
      return null;
    }

    // Clear the slot without additional fence
    ARRAY_HANDLE.setOpaque(localBuffer, index, null);

    // Advance head using opaque write (consumer-only)
    head.setOpaque(currentHead + 1);

    return (E) value;
  }

  /**
   * A relaxed version of {@link #offer(Object)} that uses only <b>opaque</b> (relaxed) reads/writes
   * for queue indices.
   *
   * @param e the element to insert (must not be null)
   * @return {@code true} if inserted, {@code false} if queue is full at the moment
   */
  @Override
  public boolean relaxedOffer(E e) {
    Objects.requireNonNull(e);

    final Object[] localBuffer = this.buffer;

    // cheaper than volatile, may see stale value
    long localProducerLimit = producerLimit.getOpaque();
    long cachedHead = 0;

    while (true) {
      long currentTail = tail.getOpaque();

      //  May observe stale producerLimit, but CAS correctness holds
      if (currentTail >= localProducerLimit) {
        cachedHead = head.getOpaque();
        localProducerLimit = cachedHead + capacity;

        // Queue still full from our viewpoint → fail fast
        if (currentTail >= localProducerLimit) {
          return false;
        }

        // Relaxed update, no store-store barrier
        producerLimit.setOpaque(localProducerLimit);
      }

      // CAS provides the actual correctness barrier
      if (tail.compareAndSet(currentTail, currentTail + 1)) {
        int index = arrayIndex(currentTail);

        //must remain Release so consumer sees the element
        ARRAY_HANDLE.setRelease(localBuffer, index, e);

        return true;
      }

      // Minimal backoff for relaxed mode
      Thread.onSpinWait();
    }
  }

  /**
   * A relaxed version of {@link #poll()} using only opaque (relaxed) memory accesses for index
   * manipulation and slot clearing.
   *
   * @return the element, or {@code null} if queue appears empty
   */
  @SuppressWarnings("unchecked")
  @Override
  public E relaxedPoll() {
    final Object[] localBuffer = this.buffer;

    long currentHead = head.getOpaque();
    int index = arrayIndex(currentHead);

    // may see stale, but producer used Release
    Object value = ARRAY_HANDLE.getOpaque(localBuffer, index);

    if (value == null) {
      return null;
    }

    ARRAY_HANDLE.setOpaque(localBuffer, index, null);
    head.setOpaque(currentHead + 1);

    return (E) value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public final E peek() {
    final int index = arrayIndex(head.getOpaque());
    return (E) ARRAY_HANDLE.getVolatile(buffer, index);
  }

  /**
   * A fully relaxed version of {@link #peek()}
   *
   * @return the element at the head, or {@code null} if empty or not yet visible
   */
  @SuppressWarnings("unchecked")
  @Override
  public E relaxedPeek() {
    long currentHead = head.getOpaque();
    int index = arrayIndex(currentHead);

    // RELAXED LOAD of element
    return (E) ARRAY_HANDLE.getOpaque(buffer, index);
  }
}
