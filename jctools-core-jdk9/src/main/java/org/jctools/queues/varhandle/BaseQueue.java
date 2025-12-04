package org.jctools.queues.varhandle;

import static org.jctools.util.Pow2.roundToPowerOfTwo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Iterator;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.varhandle.padding.PaddedLong;
import org.jctools.util.RangeUtil;

/**
 * Base class for VarHandle-based lock-free queues with optimized memory ordering.
 *
 * @param <E> element type
 */
abstract class BaseQueue<E> extends AbstractQueue<E> implements MessagePassingQueue<E> {
  // 128-byte padding for Apple Silicon, prefetch, and future CPUs
  protected static final int CACHE_LINE_SHIFT = 4;

  protected static final VarHandle ARRAY_HANDLE;

  static {
    try {
      ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  protected final int capacity;
  protected final int mask;
  protected final E[] buffer;
  protected final PaddedLong tail = new PaddedLong(); // producer index
  protected final PaddedLong head = new PaddedLong(); // consumer index

  /**
   * @param requestedCapacity queue capacity (will be rounded up to power of two)
   */
  @SuppressWarnings("unchecked")
  protected BaseQueue(int requestedCapacity) {
    this(roundToPowerOfTwo(requestedCapacity), roundToPowerOfTwo(requestedCapacity) - 1);
  }

  /**
   * @param capacity queue capacity (must be a power of two)
   * @param mask the mask
   */
  @SuppressWarnings("unchecked")
  protected BaseQueue(int capacity, int mask) {
    RangeUtil.checkGreaterThanOrEqual(capacity, 1, "capacity");
    this.capacity = capacity;
    this.mask = mask;
    this.buffer = (E[]) new Object[capacity << CACHE_LINE_SHIFT];
  }

  /**
   * Converts sequence to array index: (sequence & mask) + padding offset
   *
   * @param sequence sequence number
   * @return array index with padding offset
   */
  protected final int arrayIndex(long sequence) {
    return (int) (sequence & mask) << CACHE_LINE_SHIFT;
  }

  /**
   * @param consumer element consumer
   * @return count drained
   */
  @Override
  public final int drain(Consumer<E> consumer) {
    return MessagePassingQueueUtil.drain(this, consumer);
  }

  @Override
  public final Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  /**
   * Drains elements from the queue using a wait strategy and exit condition.
   *
   * @param c element consumer
   * @param wait wait strategy
   * @param exit exit condition
   */
  @Override
  public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit) {
    MessagePassingQueueUtil.drain(this, c, wait, exit);
  }

  /**
   * Fills the queue with elements using a wait strategy and exit condition.
   *
   * @param s element supplier
   * @param wait wait strategy
   * @param exit exit condition
   */
  @Override
  public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit) {
    MessagePassingQueueUtil.fill(this, s, wait, exit);
  }

  /**
   * @param s element supplier
   * @return count filled
   */
  @Override
  public final int fill(Supplier<E> s) {
    return MessagePassingQueueUtil.fillBounded(this, s);
  }

  /**
   * @return queue capacity
   */
  @Override
  public int capacity() {
    return capacity;
  }

  /**
   * Returns estimated size.
   *
   * @return estimated queue size
   */
  @Override
  public int size() {
    return size(1);
  }

  /**
   * Returns estimated size. May be stale due to concurrent updates. Uses sandwich technique: read
   * head, tail, head to detect races.
   *
   * @param divisor the divisor (used when the mask is not fully used for the capacity)
   * @return estimated queue size
   */
  protected final int size(int divisor) {
    long after = head.getVolatile();
    long size;
    while (true) {
      final long before = after;
      final long currentTail = tail.getVolatile();
      after = head.getVolatile();
      // "Sandwich" pattern (head-tail-head) to detect races.
      // If head unchanged, tail read is consistent. If head changed, consumer
      // polled concurrently - retry. Prevents negative/stale size from reordering.
      if (before == after) {
        size = (currentTail - after) / divisor;
        break;
      }
    }
    return sanitizeSize(size);
  }

  /**
   * Conservative empty check: may return false when poll() would return null, but never returns
   * true when elements exist.
   *
   * @return true if empty (conservative)
   */
  @Override
  public boolean isEmpty() {
    return head.getVolatile() >= tail.getVolatile();
  }

  private int sanitizeSize(long size) {
    if (size < 0) {
      return 0;
    }
    if (size > capacity) {
      return capacity;
    }
    if (size > Integer.MAX_VALUE) return Integer.MAX_VALUE;
    return (int) size;
  }

  @Override
  public final String toString() {
    return this.getClass().getName();
  }

  // Naming convention matches reference implementation:
  // lv = load volatile, la = load acquire, lp = load plain
  // sv = store volatile, so = store ordered (release), sp = store plain

  /** Load producer index with plain semantics (single producer access). */
  protected final long lpProducerIndex() {
    return tail.getPlain();
  }

  /** Load producer index with volatile semantics (full barrier). */
  protected final long lvProducerIndex() {
    return tail.getVolatile();
  }

  /** Load producer index with acquire semantics (pairs with store release). */
  protected final long laProducerIndex() {
    return tail.getAcquire();
  }

  /**
   * Store producer index with ordered (release) semantics.
   *
   * @param newValue new producer index
   */
  protected final void soProducerIndex(long newValue) {
    tail.setRelease(newValue);
  }

  /** Load consumer index with plain semantics (single consumer access). */
  protected final long lpConsumerIndex() {
    return head.getPlain();
  }

  /** Load consumer index with volatile semantics (full barrier). */
  protected final long lvConsumerIndex() {
    return head.getVolatile();
  }

  /** Load consumer index with acquire semantics (pairs with store release). */
  protected final long laConsumerIndex() {
    return head.getAcquire();
  }

  /**
   * Store consumer index with ordered (release) semantics.
   *
   * @param newValue new consumer index
   */
  protected final void soConsumerIndex(long newValue) {
    head.setRelease(newValue);
  }

  /**
   * CAS consumer index from expected to new value.
   *
   * @param expected expected current value
   * @param newValue new value to set
   * @return true if successful
   */
  protected final boolean casConsumerIndex(long expected, long newValue) {
    return head.compareAndSet(expected, newValue);
  }

  /**
   * CAS producer index from expected to new value.
   *
   * @param expected expected current value
   * @param newValue new value to set
   * @return true if successful
   */
  protected final boolean casProducerIndex(long expected, long newValue) {
    return tail.compareAndSet(expected, newValue);
  }

  /**
   * Load array element with volatile semantics (full barrier).
   *
   * @param buffer source array
   * @param index array index
   * @param <E> element type
   * @return element at index
   */
  @SuppressWarnings("unchecked")
  protected final <E> E lvRefElement(E[] buffer, int index) {
    return (E) ARRAY_HANDLE.getVolatile(buffer, index);
  }

  /**
   * Load array element with acquire semantics (pairs with store release).
   *
   * @param buffer source array
   * @param index array index
   * @param <E> element type
   * @return element at index
   */
  @SuppressWarnings("unchecked")
  protected final <E> E laRefElement(E[] buffer, int index) {
    return (E) ARRAY_HANDLE.getAcquire(buffer, index);
  }

  /**
   * Load array element with plain semantics (no ordering).
   *
   * @param buffer source array
   * @param index array index
   * @param <E> element type
   * @return element at index
   */
  @SuppressWarnings("unchecked")
  protected final <E> E lpRefElement(E[] buffer, int index) {
    return (E) ARRAY_HANDLE.get(buffer, index);
  }

  /**
   * Store array element with ordered (release) semantics.
   *
   * @param buffer target array
   * @param index array index
   * @param e element to store
   */
  protected final void soRefElement(E[] buffer, int index, Object e) {
    ARRAY_HANDLE.setRelease(buffer, index, e);
  }

  /**
   * Store array element with plain semantics (no ordering).
   *
   * @param buffer target array
   * @param index array index
   * @param e element to store
   */
  protected final void spRefElement(E[] buffer, int index, Object e) {
    ARRAY_HANDLE.set(buffer, index, e);
  }
}
