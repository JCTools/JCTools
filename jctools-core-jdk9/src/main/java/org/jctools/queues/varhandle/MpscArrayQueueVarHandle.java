package org.jctools.queues.varhandle;

import java.util.Objects;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.varhandle.padding.PaddedLong;

/**
 * MPSC bounded queue using VarHandles for lock-free producers and wait-free consumer. Producers
 * compete via CAS on tail, consumer has exclusive access to head. Memory ordering:
 * setRelease/getAcquire pairs for element visibility, plain access where exclusive.
 */
public class MpscArrayQueueVarHandle<E> extends BaseQueue<E> {
  /**
   * Cached producer limit to avoid volatile head reads. Updated lazily when exceeded. Memory
   * ordering: getVolatile/setRelease (racy updates benign).
   */
  private final PaddedLong producerLimit;

  /**
   * Store producer limit with release semantics.
   *
   * @param value new producer limit
   */
  private void soProducerLimit(long value) {
    producerLimit.setRelease(value);
  }

  /**
   * Load producer limit with acquire semantics.
   *
   * @return current producer limit
   */
  private long laProducerLimit() {
    return producerLimit.getAcquire();
  }

  /**
   * @param requestedCapacity queue capacity (rounded up to power of two)
   */
  public MpscArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
    this.producerLimit = new PaddedLong(capacity);
  }

  /**
   * Offers element if queue size is below threshold.
   *
   * @param e element to offer (not null)
   * @param threshold maximum allowable size
   * @return true if offered, false if size exceeds threshold
   */
  public boolean offerIfBelowThreshold(final E e, int threshold) {
    if (null == e) {
      throw new NullPointerException();
    }

    final long mask = this.mask;
    final long capacity = mask + 1;

    long producerLimit = laProducerLimit(); // Acquire: pairs with other producers' setRelease
    long pIndex;
    do {
      pIndex = laProducerIndex(); // Acquire: pairs with other producers' CAS release
      long available = producerLimit - pIndex;
      long size = capacity - available;
      if (size >= threshold) {
        final long cIndex = laConsumerIndex(); // Acquire: pairs with consumer's setRelease
        size = pIndex - cIndex;
        if (size >= threshold) {
          return false; // the size exceeds threshold
        } else {
          // update producer limit to the next index that we must recheck the consumer index
          producerLimit = cIndex + capacity;

          // this is racy, but the race is benign
          soProducerLimit(producerLimit); // Release: publish limit to other producers
        }
      }
    } while (!casProducerIndex(pIndex, pIndex + 1)); // CAS: full barrier
    /*
     * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
     * the index visibility to poll() we would need to handle the case where the element is not visible.
     */

    // Won CAS, move on to storing
    soRefElement(buffer, arrayIndex(pIndex), e); // Release: publish element to consumer
    return true; // AWESOME :)
  }

  /**
   * {@inheritDoc} <br>
   *
   * <p>IMPLEMENTATION NOTES:<br>
   * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
   * concurrently.
   *
   * @see java.util.Queue#offer
   * @see org.jctools.queues.MessagePassingQueue#offer
   */
  @Override
  public boolean offer(final E e) {
    if (null == e) {
      throw new NullPointerException();
    }

    // use a cached view on consumer index (potentially updated in loop)
    final long mask = this.mask;
    long producerLimit = laProducerLimit(); // Acquire: pairs with other producers' setRelease
    long pIndex;
    do {
      pIndex = laProducerIndex(); // Acquire: pairs with other producers' CAS release
      if (pIndex >= producerLimit) {
        final long cIndex = laConsumerIndex(); // Acquire: pairs with consumer's setRelease
        producerLimit = cIndex + mask + 1;

        if (pIndex >= producerLimit) {
          return false; // FULL :(
        } else {
          // update producer limit to the next index that we must recheck the consumer index
          // this is racy, but the race is benign
          soProducerLimit(producerLimit); // Release: publish limit to other producers
        }
      }
    } while (!casProducerIndex(pIndex, pIndex + 1)); // CAS: full barrier
    /*
     * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
     * the index visibility to poll() we would need to handle the case where the element is not visible.
     */

    // Won CAS, move on to storing
    soRefElement(buffer, arrayIndex(pIndex), e); // Release: publish element to consumer
    return true; // AWESOME :)
  }

  /**
   * A wait free alternative to offer which fails on CAS failure.
   *
   * @param e new element, not null
   * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
   */
  public final int failFastOffer(final E e) {
    Objects.requireNonNull(e);
    final long mask = this.mask;
    final long capacity = mask + 1;
    final long pIndex = laProducerIndex(); // Acquire: pairs with other producers' CAS release
    long producerLimit = laProducerLimit(); // Acquire: pairs with other producers' setRelease
    if (pIndex >= producerLimit) {
      final long cIndex = laConsumerIndex(); // Acquire: pairs with consumer's setRelease
      producerLimit = cIndex + capacity;
      if (pIndex >= producerLimit) {
        return 1; // FULL :(
      } else {
        // update producer limit to the next index that we must recheck the consumer index
        soProducerLimit(producerLimit); // Release: publish limit to other producers
      }
    }

    // look Ma, no loop!
    if (!casProducerIndex(pIndex, pIndex + 1)) { // CAS: full barrier
      return -1; // CAS FAIL :(
    }

    // Won CAS, move on to storing
    soRefElement(buffer, arrayIndex(pIndex), e); // Release: publish element to consumer
    return 0; // AWESOME :)
  }

  /**
   * {@inheritDoc}
   *
   * <p>IMPLEMENTATION NOTES:<br>
   * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single
   * thread.
   *
   * @see java.util.Queue#poll
   * @see org.jctools.queues.MessagePassingQueue#poll
   */
  @Override
  public E poll() {
    final long cIndex = lpConsumerIndex(); // Plain: single consumer, no contention
    final int arrayConsumerIndex = arrayIndex(cIndex);
    // Copy field to avoid re-reading after volatile load
    final E[] buffer = this.buffer;

    // If we can't see the next available element we can't poll
    E e = laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
    if (null == e) {
      /*
       * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
       * winning the CAS on offer but before storing the element in the queue. Other producers may go on
       * to fill up the queue after this element.
       */
      if (cIndex != laProducerIndex()) { // Acquire: pairs with producer's CAS release
        do {
          e = laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
        } while (e == null);
      } else {
        return null;
      }
    }

    spRefElement(buffer, arrayConsumerIndex, null); // Plain: single consumer, no need to publish
    soConsumerIndex(cIndex + 1); // Release: publish head advance to producers
    return e;
  }

  /**
   * {@inheritDoc}
   *
   * <p>IMPLEMENTATION NOTES:<br>
   * Lock free peek using ordered loads. As class name suggests access is limited to a single
   * thread.
   *
   * @see java.util.Queue#poll
   * @see org.jctools.queues.MessagePassingQueue#poll
   */
  @Override
  public E peek() {
    // Copy field to avoid re-reading after volatile load
    final E[] buffer = this.buffer;

    final long cIndex = lpConsumerIndex(); // Plain: single consumer, no contention
    final int arrayConsumerIndex = arrayIndex(cIndex);
    E e = laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
    if (null == e) {
      /*
       * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
       * winning the CAS on offer but before storing the element in the queue. Other producers may go on
       * to fill up the queue after this element.
       */
      if (cIndex != laProducerIndex()) { // Acquire: pairs with producer's CAS release
        do {
          e = laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
        } while (e == null);
      } else {
        return null;
      }
    }
    return e;
  }

  @Override
  public boolean relaxedOffer(E e) {
    return offer(e);
  }

  @Override
  public E relaxedPoll() {
    final E[] buffer = this.buffer;
    final long cIndex = lpConsumerIndex(); // Plain: single consumer, no contention
    final int arrayConsumerIndex = arrayIndex(cIndex);

    // If we can't see the next available element we can't poll
    E e = laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
    if (null == e) {
      return null;
    }

    spRefElement(buffer, arrayConsumerIndex, null); // Plain: single consumer, no need to publish
    soConsumerIndex(cIndex + 1); // Release: publish head advance to producers
    return e;
  }

  @Override
  public E relaxedPeek() {
    final E[] buffer = this.buffer;
    final long cIndex = lpConsumerIndex(); // Plain: single consumer, no contention
    return laRefElement(buffer, arrayIndex(cIndex)); // Acquire: pairs with producer's setRelease
  }

  /**
   * Drains up to limit elements from the queue.
   *
   * @param c element consumer
   * @param limit max elements to drain
   * @return number of elements drained
   */
  @Override
  public int drain(final MessagePassingQueue.Consumer<E> c, final int limit) {
    if (null == c) {
      throw new IllegalArgumentException("c is null");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("limit is negative: " + limit);
    }
    if (limit == 0) {
      return 0;
    }

    final E[] buffer = this.buffer;
    final long cIndex = lpConsumerIndex(); // Plain: single consumer, no contention

    for (int i = 0; i < limit; i++) {
      final long index = cIndex + i;
      final int arrayConsumerIndex = arrayIndex(index);
      final E e =
          laRefElement(buffer, arrayConsumerIndex); // Acquire: pairs with producer's setRelease
      if (null == e) {
        return i;
      }
      spRefElement(buffer, arrayConsumerIndex, null); // Plain: single consumer, no need to publish
      soConsumerIndex(
          index + 1); // Release: publish head advance to producers (ordered store -> atomic and
      // ordered for size())
      c.accept(e);
    }
    return limit;
  }

  /**
   * Fills queue with up to limit elements from supplier.
   *
   * @param s element supplier
   * @param limit max elements to fill
   * @return number of elements filled
   */
  @Override
  public int fill(MessagePassingQueue.Supplier<E> s, int limit) {
    if (null == s) {
      throw new IllegalArgumentException("supplier is null");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("limit is negative:" + limit);
    }
    if (limit == 0) {
      return 0;
    }

    final long mask = this.mask;
    final long capacity = mask + 1;
    long producerLimit = laProducerLimit(); // Acquire: pairs with other producers' setRelease
    long pIndex;
    int actualLimit;
    do {
      pIndex = laProducerIndex(); // Acquire: pairs with other producers' CAS release
      long available = producerLimit - pIndex;
      if (available <= 0) {
        final long cIndex = laConsumerIndex(); // Acquire: pairs with consumer's setRelease
        producerLimit = cIndex + capacity;
        available = producerLimit - pIndex;
        if (available <= 0) {
          return 0; // FULL :(
        } else {
          // update producer limit to the next index that we must recheck the consumer index
          soProducerLimit(producerLimit); // Release: publish limit to other producers
        }
      }
      actualLimit = Math.min((int) available, limit);
    } while (!casProducerIndex(pIndex, pIndex + actualLimit)); // CAS: full barrier
    // right, now we claimed a few slots and can fill them with goodness
    final E[] buffer = this.buffer;
    for (int i = 0; i < actualLimit; i++) {
      // Won CAS, move on to storing
      soRefElement(buffer, arrayIndex(pIndex + i), s.get()); // Release: publish element to consumer
    }
    return actualLimit;
  }

  /**
   * Drains elements using wait strategy and exit condition.
   *
   * @param c element consumer
   * @param w wait strategy
   * @param exit exit condition
   */
  @Override
  public void drain(
      MessagePassingQueue.Consumer<E> c,
      MessagePassingQueue.WaitStrategy w,
      MessagePassingQueue.ExitCondition exit) {
    MessagePassingQueueUtil.drain(this, c, w, exit);
  }

  /**
   * Fills queue using wait strategy and exit condition.
   *
   * @param s element supplier
   * @param wait wait strategy
   * @param exit exit condition
   */
  @Override
  public void fill(
      MessagePassingQueue.Supplier<E> s,
      MessagePassingQueue.WaitStrategy wait,
      MessagePassingQueue.ExitCondition exit) {
    MessagePassingQueueUtil.fill(this, s, wait, exit);
  }
}
