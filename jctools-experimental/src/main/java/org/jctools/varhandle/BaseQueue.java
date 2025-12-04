package org.jctools.varhandle;

import static org.jctools.util.Pow2.roundToPowerOfTwo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.varhandle.padding.PaddedLong;

/**
 * Base class for non-blocking queuing operations.
 *
 * @param <E> the type of elements held by this queue
 */
abstract class BaseQueue<E> extends AbstractQueue<E> implements MessagePassingQueue<E> {
  private static final int CACHE_LINE_LONGS = 8; // 64 bytes / 8 bytes per long/ref
  protected static final VarHandle ARRAY_HANDLE;

  static {
    try {
      ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  /** The capacity of the queue (must be a power of two) */
  protected final int capacity;

  /** Mask for fast modulo operation (index = pos & mask) */
  protected final int mask;

  /** The backing array (plain Java array for VarHandle access) */
  protected final Object[] buffer;

  // Padding to avoid false sharing
  @SuppressWarnings("unused")
  private long p0, p1, p2, p3, p4, p5, p6;

  /** Next free slot for producer (single-threaded) */
  protected final PaddedLong tail = new PaddedLong();

  /** Next slot to consume (multi-threaded) */
  protected final PaddedLong head = new PaddedLong();

  // Padding around head
  @SuppressWarnings("unused")
  private long r0, r1, r2, r3, r4, r5, r6;

  public BaseQueue(int requestedCapacity) {
    this.capacity = roundToPowerOfTwo(requestedCapacity);
    this.mask = this.capacity - 1;
    this.buffer = new Object[capacity + 2 * CACHE_LINE_LONGS];
  }

  protected final int arrayIndex(long sequence) {
    return (int) (sequence & mask) + CACHE_LINE_LONGS;
  }

  public int drain(Consumer<E> consumer) {
    return MessagePassingQueueUtil.drain(this, consumer);
  }

  public int drain(Consumer<E> consumer, int limit) {
    int count = 0;
    E e;
    while (count < limit && (e = poll()) != null) {
      consumer.accept(e);
      count++;
    }
    return count;
  }

  /**
   * Iterator is not supported.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public final Iterator<E> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit) {
    MessagePassingQueueUtil.drain(this, c, wait, exit);
  }

  @Override
  public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit) {
    MessagePassingQueueUtil.fill(this, s, wait, exit);
  }

  @Override
  public int fill(Supplier<E> s, int limit) {
    if (null == s) {
      throw new IllegalArgumentException("supplier is null");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("limit is negative:" + limit);
    }

    if (limit == 0) {
      return 0;
    }

    int added = 0;
    while (added < limit) {
      E e = s.get();
      if (e == null) {
        break; // stop if supplier exhausted
      }

      if (offer(e)) {
        added++;
      } else {
        break; // queue is full
      }
    }
    return added;
  }

  @Override
  public int fill(Supplier<E> s) {
    return MessagePassingQueueUtil.fillBounded(this, s);
  }

  @Override
  public final int capacity() {
    return capacity;
  }

  @Override
  public final int size() {
    long currentTail = tail.getVolatile();
    long currentHead = head.getVolatile();
    return (int) (currentTail - currentHead);
  }
}
