package org.jctools.varhandle;

import java.util.Objects;

/**
 * A high-performance Single-Producer, Single-Consumer (SPSC) bounded queue using a circular buffer.
 *
 * @param <E> the type of elements held in this queue
 */
final class SpscArrayQueueVarHandle<E> extends BaseQueue<E> {
  // These caches eliminate redundant volatile reads
  private long cachedHead = 0L; // visible only to producer
  private long cachedTail = 0L; // visible only to consumer

  /**
   * Creates a new SPSC queue with the specified capacity. Capacity must be a power of two.
   *
   * @param requestedCapacity the desired capacity, rounded up to the next power of two if needed
   */
  public SpscArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
  }

  @Override
  public boolean offer(E e) {
    Objects.requireNonNull(e);

    final long currentTail = tail.getOpaque();
    final int index = arrayIndex(currentTail);

    if (currentTail - cachedHead >= capacity) {
      // Refresh cached head (read from consumer side)
      cachedHead = (long) head.getVolatile();
      if (currentTail - cachedHead >= capacity) {
        return false; // still full
      }
    }

    ARRAY_HANDLE.setRelease(buffer, index, e); // publish value
    tail.setOpaque(currentTail + 1); // relaxed tail update
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public E poll() {
    final long currentHead = head.getOpaque();
    final int index = arrayIndex(currentHead);

    if (currentHead >= cachedTail) {
      // refresh tail cache
      cachedTail = tail.getVolatile();
      if (currentHead >= cachedTail) {
        return null; // still empty
      }
    }

    Object value = ARRAY_HANDLE.getAcquire(buffer, index);
    ARRAY_HANDLE.setOpaque(buffer, index, null); // clear slot
    head.setOpaque(currentHead + 1); // relaxed head update
    return (E) value;
  }

  @Override
  @SuppressWarnings("unchecked")
  public E peek() {
    return (E) ARRAY_HANDLE.getVolatile(buffer, arrayIndex(head.getOpaque()));
  }

  @Override
  public boolean relaxedOffer(E e) {
    return offer(e);
  }

  @Override
  public E relaxedPoll() {
    return poll();
  }

  @Override
  public E relaxedPeek() {
    return peek();
  }
}
