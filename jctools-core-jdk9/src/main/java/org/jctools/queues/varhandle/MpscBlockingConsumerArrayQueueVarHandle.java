/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues.varhandle;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.varhandle.padding.PaddedLong;
import org.jctools.queues.varhandle.padding.PaddedThread;
import org.jctools.util.Pow2;

/**
 * Multiple-Producer, Single-Consumer bounded lock-free queue with blocking consumer support.
 * Producers are lock-free (CAS-based), consumer is wait-free. Uses VarHandles for memory ordering.
 *
 * @param <E> the type of elements stored
 * @author <a href="https://github.com/amarziali">Andrea Marziali</a>
 */
public final class MpscBlockingConsumerArrayQueueVarHandle<E> extends BaseQueue<E>
    implements MessagePassingQueue<E>, BlockingQueue<E> {

  /**
   * Cached producer limit to avoid volatile head reads. Updated lazily when exceeded. Memory
   * ordering: getVolatile/setRelease (racy updates benign).
   */
  private final PaddedLong producerLimit;

  /** The consumer thread that to be woken up if the customer blocked on take() */
  private final PaddedThread blocked = new PaddedThread();

  /**
   * Load producer limit with opaque semantics (best for hot path). Opaque prevents compiler
   * reordering but has no CPU fence overhead. Safe because racy updates are benign - stale reads
   * just trigger recalculation.
   *
   * @return current producer limit
   */
  private long lpProducerLimit() {
    return producerLimit.getOpaque();
  }

  /**
   * Compare and Swap for the producer limit.
   *
   * @param expect
   * @param update
   * @return
   */
  private boolean casProducerLimit(long expect, long update) {
    return producerLimit.compareAndSet(expect, update);
  }

  /**
   * Store blocked thread with release semantics.
   *
   * @param value new value to be set
   */
  private void soBlocked(Thread value) {
    blocked.setRelease(value);
  }

  /**
   * Load blocked thread with opaque semantics (best for hot path). Safe because missing the blocked
   * thread just causes a retry, and subsequent CAS provides a full fence anyway.
   *
   * @return current blocked thread if any
   */
  private Thread lpBlocked() {
    return blocked.getOpaque();
  }

  /**
   * Creates queue with specified capacity.
   *
   * @param requestedCapacity queue capacity (rounded up to power of two)
   */
  public MpscBlockingConsumerArrayQueueVarHandle(final int requestedCapacity) {
    // leave lower bit of mask clear
    super(
        Pow2.roundToPowerOfTwo(requestedCapacity),
        (Pow2.roundToPowerOfTwo(requestedCapacity) - 1) << 1);
    producerLimit = new PaddedLong(mask); // we know it's all empty to start with
  }

  /**
   * Converts sequence to array index: (sequence & mask) + padding offset
   *
   * @param sequence sequence number
   * @return array index with padding offset
   */
  private int modifiedArrayIndex(long sequence) {
    return (int) (sequence & mask) << (CACHE_LINE_SHIFT - 1);
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
    final long capacity = mask + 2;
    threshold = threshold << 1;
    final E[] buffer = this.buffer;
    long pIndex;
    while (true) {
      pIndex = lvProducerIndex();
      // lower bit is indicative of blocked consumer
      if ((pIndex & 1) == 1) {
        if (offerAndWakeup(buffer, pIndex, e)) {
          return true;
        }
        continue;
      }
      // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1), consumer is awake
      final long producerLimit = lpProducerLimit(); // opaque read - fastest, racy updates benign

      // Use producer limit to save a read of the more rapidly mutated consumer index.
      // Assumption: queue is usually empty or near empty

      // available is also << 1
      final long available = producerLimit - pIndex;
      // sizeEstimate <= size
      final long sizeEstimate = capacity - available;

      if (sizeEstimate >= threshold
          ||
          // producerLimit check allows for threshold >= capacity
          producerLimit <= pIndex) {
        if (!recalculateProducerLimit(
            pIndex, producerLimit, lvConsumerIndex(), capacity, threshold)) {
          return false;
        }
      }

      // Claim the index
      if (casProducerIndex(pIndex, pIndex + 2)) {
        break;
      }
    }
    // INDEX visible before ELEMENT
    soRefElement(buffer, modifiedArrayIndex(pIndex), e); // release element e
    return true;
  }

  /**
   * Offers element to queue (lock-free for producers).
   *
   * @param e element to offer (not null)
   * @return true if offered, false if full
   */
  @Override
  public boolean offer(final E e) {
    Objects.requireNonNull(e);

    final long mask = this.mask;
    final E[] buffer = this.buffer;
    long pIndex;
    while (true) {
      pIndex = lvProducerIndex();
      // lower bit is indicative of blocked consumer
      if ((pIndex & 1) == 1) {
        if (offerAndWakeup(buffer, pIndex, e)) {
          return true;
        }
        continue;
      }
      // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1), consumer is awake
      final long producerLimit = lpProducerLimit(); // opaque read - fastest, racy updates benign

      // Use producer limit to save a read of the more rapidly mutated consumer index.
      // Assumption: queue is usually empty or near empty
      if (producerLimit <= pIndex) {
        if (!recalculateProducerLimit(mask, pIndex, producerLimit)) {
          return false;
        }
      }

      // Claim the index
      if (casProducerIndex(pIndex, pIndex + 2)) {
        break;
      }
    }
    // INDEX visible before ELEMENT
    soRefElement(buffer, modifiedArrayIndex(pIndex), e); // release element e
    return true;
  }

  /**
   * Inserts element, throwing exception if full (unsupported operation).
   *
   * @param e element to add
   * @throws InterruptedException if interrupted
   */
  @Override
  public void put(E e) throws InterruptedException {
    if (!offer(e)) throw new UnsupportedOperationException();
  }

  /**
   * Offers element with timeout (unsupported operation).
   *
   * @param e element to add
   * @param timeout timeout value
   * @param unit time unit
   * @return true if added
   * @throws InterruptedException if interrupted
   */
  @Override
  public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
    if (offer(e)) return true;
    throw new UnsupportedOperationException();
  }

  private boolean offerAndWakeup(E[] buffer, long pIndex, E e) {
    // pIndex is odd when consumer is blocked, representing the slot at pIndex-1
    final int index = modifiedArrayIndex(pIndex - 1);
    final Thread consumerThread = lpBlocked(); // opaque read - faster, CAS below provides fence

    // We could see a null here through a race with the consumer not yet storing the reference. Just
    // retry.
    if (consumerThread == null) {
      return false;
    }

    // Claim the slot and the responsibility of unparking
    if (!casProducerIndex(pIndex, pIndex + 1)) {
      return false;
    }

    soRefElement(buffer, index, e);
    LockSupport.unpark(consumerThread);
    return true;
  }

  private boolean recalculateProducerLimit(long mask, long pIndex, long producerLimit) {
    return recalculateProducerLimit(pIndex, producerLimit, lvConsumerIndex(), mask + 2, mask + 2);
  }

  private boolean recalculateProducerLimit(
      long pIndex, long producerLimit, long cIndex, long bufferCapacity, long threshold) {
    // try to update the limit with our new found knowledge on cIndex
    if (cIndex + bufferCapacity > pIndex) {
      casProducerLimit(producerLimit, cIndex + bufferCapacity);
    }
    // full and cannot grow, or hit threshold
    long size = pIndex - cIndex;
    return size < threshold && size < bufferCapacity;
  }

  /**
   * Takes element, blocking if empty (single consumer only).
   *
   * @return head element
   * @throws InterruptedException if interrupted
   */
  @Override
  public E take() throws InterruptedException {
    final E[] buffer = this.buffer;

    final long cIndex = lpConsumerIndex();
    final int arrayConsumerIndex = modifiedArrayIndex(cIndex);
    E e = lvRefElement(buffer, arrayConsumerIndex);
    if (e == null) {
      return parkUntilNext(cIndex, arrayConsumerIndex, Long.MAX_VALUE);
    }

    soRefElement(buffer, arrayConsumerIndex, null); // release element null
    soConsumerIndex(cIndex + 2); // release cIndex

    return e;
  }

  /**
   * Polls element with timeout (single consumer only).
   *
   * @param timeout max wait time
   * @param unit time unit
   * @return head element or null if timeout
   * @throws InterruptedException if interrupted
   */
  @Override
  public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    final E[] buffer = this.buffer;

    final long cIndex = lpConsumerIndex();
    final int arrayConsumerIndex = modifiedArrayIndex(cIndex);
    E e = lvRefElement(buffer, arrayConsumerIndex);
    if (e == null) {
      long timeoutNs = unit.toNanos(timeout);
      if (timeoutNs <= 0) {
        return null;
      }
      return parkUntilNext(cIndex, arrayConsumerIndex, timeoutNs);
    }

    soRefElement(buffer, arrayConsumerIndex, null); // release element null
    soConsumerIndex(cIndex + 2); // release cIndex

    return e;
  }

  /**
   * Parks consumer thread until element available or timeout.
   *
   * @param cIndex consumer index
   * @param arrayIndex array slot index
   * @param timeoutNs timeout in nanoseconds (Long.MAX_VALUE for indefinite)
   * @return element found or null on timeout
   * @throws InterruptedException if interrupted
   */
  private E parkUntilNext(long cIndex, int arrayIndex, long timeoutNs) throws InterruptedException {
    E e;
    final long pIndex = lvProducerIndex();
    if (cIndex == pIndex
        && // queue is empty
        casProducerIndex(pIndex, pIndex + 1)) // we announce ourselves as parked by setting parity
    {
      // producers only try a wakeup when both the index and the blocked thread are visible,
      // otherwise they spin
      soBlocked(Thread.currentThread());
      // ignore deadline when it's forever
      final long deadlineNs = timeoutNs == Long.MAX_VALUE ? 0 : System.nanoTime() + timeoutNs;

      try {
        while (true) {
          LockSupport.parkNanos(this, timeoutNs);
          if (Thread.interrupted()) {
            casProducerIndex(pIndex + 1, pIndex);
            throw new InterruptedException();
          }
          if ((lvProducerIndex() & 1) == 0) {
            break;
          }
          // ignore deadline when it's forever
          timeoutNs = timeoutNs == Long.MAX_VALUE ? Long.MAX_VALUE : deadlineNs - System.nanoTime();
          if (timeoutNs <= 0) {
            if (casProducerIndex(pIndex + 1, pIndex)) {
              // ran out of time and the producer has not moved the index
              return null;
            }

            break; // just in the nick of time
          }
        }
      } finally {
        soBlocked(null);
      }
    }
    // producer index is visible before element, so if we wake up between the index moving and the
    // element
    // store we could see a null.
    e = spinWaitForElement(arrayIndex);

    soRefElement(buffer, arrayIndex, null); // release element null
    soConsumerIndex(cIndex + 2); // release cIndex

    return e;
  }

  /**
   * Returns remaining capacity.
   *
   * @return available space in queue
   */
  @Override
  public int remainingCapacity() {
    return capacity() - size();
  }

  /**
   * Drains to collection (unsupported operation).
   *
   * @param c target collection
   * @return number drained
   */
  @Override
  public int drainTo(Collection<? super E> c) {
    throw new UnsupportedOperationException();
  }

  /**
   * Drains to collection with limit (unsupported operation).
   *
   * @param c target collection
   * @param maxElements max elements to drain
   * @return number drained
   */
  @Override
  public int drainTo(Collection<? super E> c, int maxElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Polls element from queue (single consumer only).
   *
   * @return head element or null if empty
   */
  @Override
  public E poll() {
    final E[] buffer = this.buffer;

    final long index = lpConsumerIndex();
    final int consumerArrayIndex = modifiedArrayIndex(index);
    E e = lvRefElement(buffer, consumerArrayIndex);
    if (e == null) {
      // consumer can't see the odd producer index
      if (index != lvProducerIndex()) {
        // poll() == null iff queue is empty, null element is not strong enough indicator, so we
        // must
        // check the producer index. If the queue is indeed not empty we spin until element is
        // visible.
        e = spinWaitForElement(consumerArrayIndex);
      } else {
        return null;
      }
    }

    soRefElement(buffer, consumerArrayIndex, null); // release element null
    soConsumerIndex(index + 2); // release cIndex
    return e;
  }

  private E spinWaitForElement(int index) {
    E e;
    while ((e = laRefElement(buffer, index)) == null) {
      Thread.onSpinWait();
    }
    return e;
  }

  /**
   * Peeks at head element (single consumer only).
   *
   * @return head element or null if empty
   */
  @Override
  public E peek() {
    final E[] buffer = this.buffer;

    final long index = lpConsumerIndex();
    final int arrayIndex = modifiedArrayIndex(index);
    E e = lvRefElement(buffer, arrayIndex);
    if (e == null && index != lvProducerIndex()) {
      // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
      // check the producer index. If the queue is indeed not empty we spin until element is
      // visible.
      e = spinWaitForElement(arrayIndex);
    }

    return e;
  }

  /**
   * Returns queue capacity.
   *
   * @return queue capacity
   */
  @Override
  public int capacity() {
    return ((mask + 2) >> 1);
  }

  /**
   * Checks if queue is empty, accounting for blocked consumer state.
   *
   * @return true if empty
   */
  @Override
  public boolean isEmpty() {
    // Mask off the low bit of pIndex to handle blocked consumer case
    // When consumer is blocked, pIndex is odd but queue is still empty
    return head.getVolatile() >= (tail.getVolatile() & ~1L);
  }

  /**
   * Returns estimated size.
   *
   * @return estimated queue size
   */
  @Override
  public int size() {
    return size(2);
  }

  /**
   * Relaxed offer, delegates to offer.
   *
   * @param e element to offer
   * @return true if offered, false if full
   */
  @Override
  public boolean relaxedOffer(E e) {
    return offer(e);
  }

  /**
   * Relaxed poll without full guarantees.
   *
   * @return head element or null if empty
   */
  @Override
  public E relaxedPoll() {
    final E[] buffer = this.buffer;
    final long index = lpConsumerIndex();

    final int arrayIndex = modifiedArrayIndex(index);
    E e = lvRefElement(buffer, arrayIndex);
    if (e == null) {
      return null;
    }
    soRefElement(buffer, arrayIndex, null);
    soConsumerIndex(index + 2);
    return e;
  }

  /**
   * Relaxed peek without full guarantees.
   *
   * @return head element or null if empty
   */
  @Override
  public E relaxedPeek() {
    final E[] buffer = this.buffer;
    final long index = lpConsumerIndex();

    return lvRefElement(buffer, modifiedArrayIndex(index));
  }

  /**
   * Fills queue with up to limit elements from supplier (batch operation).
   *
   * @param s element supplier
   * @param limit max elements to fill
   * @return number of elements filled
   */
  @Override
  public int fill(Supplier<E> s, int limit) {
    if (null == s) throw new IllegalArgumentException("supplier is null");
    if (limit < 0) throw new IllegalArgumentException("limit is negative:" + limit);
    if (limit == 0) return 0;

    long pIndex;
    int claimedSlots;
    Thread blockedConsumer = null;
    long batchLimit = 0;
    final long shiftedBatchSize = 2L * limit;

    while (true) {
      pIndex = lvProducerIndex();
      long producerLimit = lpProducerLimit(); // opaque read - fastest, racy updates benign

      // lower bit is indicative of blocked consumer
      if ((pIndex & 1) == 1) {
        // observe the blocked thread for the pIndex
        blockedConsumer = lpBlocked(); // opaque read - faster, CAS below provides fence
        if (blockedConsumer == null) continue; // racing, retry
        if (!casProducerIndex(pIndex, pIndex + 1)) {
          blockedConsumer = null;
          continue;
        }
        // We have observed the blocked thread for the pIndex(lv index, lv thread, cas index).
        // We've claimed pIndex, now we need to wake up consumer and set the element
        batchLimit = pIndex + 1;
        pIndex = pIndex - 1;
        break;
      }
      // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1), consumer is awake

      // Calculate available space (similar to reference implementation)
      long available = producerLimit - pIndex;

      // Use producer limit to save a read of the more rapidly mutated consumer index.
      // Assumption: queue is usually empty or near empty
      if (available < shiftedBatchSize) {
        if (!recalculateProducerLimit(mask, pIndex, producerLimit)) {
          return 0;
        }
        producerLimit = lpProducerLimit();
        available = producerLimit - pIndex;
        if (available <= 0) {
          return 0; // FULL
        }
      }

      // we want 'limit' slots, but will settle for whatever is available
      long claimedShifted = Math.min(available, shiftedBatchSize);
      batchLimit = pIndex + claimedShifted;

      // Claim the index
      if (casProducerIndex(pIndex, batchLimit)) {
        break;
      }
    }
    claimedSlots = (int) ((batchLimit - pIndex) / 2);

    final E[] buffer = this.buffer;
    // first element offset might be a wakeup, so peeled from loop
    for (int i = 0; i < claimedSlots; i++) {
      soRefElement(buffer, modifiedArrayIndex(pIndex + 2L * i), s.get());
    }

    if (blockedConsumer != null) {
      // no point unblocking an unrelated blocked thread, things have obviously moved on
      if (lpBlocked() == blockedConsumer) {
        LockSupport.unpark(blockedConsumer);
      }
    }

    return claimedSlots;
  }

  /**
   * Drains up to limit elements with timeout (single consumer only).
   *
   * @param c element consumer
   * @param limit max elements to drain
   * @param timeout max wait time
   * @param unit time unit
   * @return number of elements drained
   * @throws InterruptedException if interrupted
   */
  public int drain(Consumer<E> c, final int limit, long timeout, TimeUnit unit)
      throws InterruptedException {
    if (limit == 0) {
      return 0;
    }
    final int drained = drain(c, limit);
    if (drained != 0) {
      return drained;
    }
    final E e = poll(timeout, unit);
    if (e == null) return 0;
    c.accept(e);
    return 1 + drain(c, limit - 1);
  }

  /**
   * Drains up to limit elements from queue.
   *
   * @param c element consumer
   * @param limit max elements to drain
   * @return number of elements drained
   */
  @Override
  public int drain(final Consumer<E> c, final int limit) {
    return MessagePassingQueueUtil.drain(this, c, limit);
  }

  /**
   * Drains elements using wait strategy and exit condition.
   *
   * @param c element consumer
   * @param w wait strategy
   * @param exit exit condition
   */
  @Override
  public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit) {
    MessagePassingQueueUtil.drain(this, c, w, exit);
  }
}
