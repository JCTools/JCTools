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

import org.jctools.queues.MessagePassingQueue;
import org.jctools.queues.MessagePassingQueueUtil;
import org.jctools.queues.varhandle.padding.PaddedLong;

/**
 * Single-Producer Multiple-Consumer bounded lock-free queue on circular array. Producer operations
 * are wait-free; consumer operations are lock-free. Producer uses plain reads and release writes;
 * consumers use CAS and volatile reads.
 *
 * @param <E> the element type
 * @author <a href="https://github.com/amarziali">Andrea Marziali</a>
 */
public class SpmcArrayQueueVarHandle<E> extends BaseQueue<E> implements MessagePassingQueue<E> {

  private final PaddedLong producerIndexCache = new PaddedLong();

  /**
   * Store producer index cache with volatile semantics.
   *
   * @param value new cache value
   */
  protected final void svProducerIndexCache(long value) {
    producerIndexCache.setVolatile(value);
  }

  /**
   * Load producer index cache with volatile semantics.
   *
   * @return cached producer index
   */
  protected final long lvProducerIndexCache() {
    return producerIndexCache.getVolatile();
  }

  /**
   * @param requestedCapacity queue capacity (rounded up to power of two)
   */
  public SpmcArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
  }

  /**
   * Offers element to queue.
   *
   * @param e element to offer (not null)
   * @return true if offered, false if full
   */
  @Override
  public boolean offer(final E e) {
    if (null == e) {
      throw new NullPointerException();
    }
    final E[] buffer = this.buffer;
    final long mask = this.mask;
    final long currProducerIndex = lpProducerIndex(); // Plain: single producer, exclusive access
    final int arrayProducerOffset = arrayIndex(currProducerIndex);
    if (null
        != laRefElement(
            buffer, arrayProducerOffset)) { // Acquire: pairs with consumer's setRelease(null)
      long size =
          currProducerIndex - laConsumerIndex(); // Acquire: pairs with consumer's CAS release

      if (size > mask) {
        return false;
      } else {
        // Bubble: This can happen because `poll` moves index before placing element.
        // spin wait for slot to clear, buggers wait freedom
        while (null
            != laRefElement(
                buffer, arrayProducerOffset)) { // Acquire: pairs with consumer's setRelease(null)
          Thread.onSpinWait();
        }
      }
    }
    soRefElement(buffer, arrayProducerOffset, e); // Release: publish element to consumers
    // single producer, so store ordered is valid. It is also required to correctly publish the
    // element
    // and for the consumers to pick up the tail value.
    soProducerIndex(currProducerIndex + 1); // Release: publish tail advance to consumers
    return true;
  }

  /**
   * Polls element from queue.
   *
   * @return head element or null if empty
   */
  @Override
  public E poll() {
    long currentConsumerIndex;
    long currProducerIndexCache =
        lvProducerIndexCache(); // Volatile: inter-consumer cache coordination
    do {
      currentConsumerIndex =
          lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
      if (currentConsumerIndex >= currProducerIndexCache) {
        long currProducerIndex = laProducerIndex(); // Acquire: pairs with producer's setRelease
        if (currentConsumerIndex >= currProducerIndex) {
          return null;
        } else {
          currProducerIndexCache = currProducerIndex;
          svProducerIndexCache(currProducerIndex); // Volatile: inter-consumer cache coordination
        }
      }
    } while (!casConsumerIndex(
        currentConsumerIndex, currentConsumerIndex + 1)); // CAS: claim slot, full barrier
    // consumers are gated on latest visible tail, and so can't see a null value in the queue or
    // overtake
    // and wrap to hit same location.
    return removeElement(buffer, currentConsumerIndex, mask);
  }

  private E removeElement(final E[] buffer, long index, final long mask) {
    final int arrayIndex = arrayIndex(index);
    // load plain, element happens before it's index becomes visible
    final E e = lpRefElement(buffer, arrayIndex);
    // store ordered, make sure nulling out is visible. Producer is waiting for this value.
    soRefElement(buffer, arrayIndex, null);
    return e;
  }

  /**
   * Peeks at head element.
   *
   * @return head element or null if empty
   */
  @Override
  public E peek() {
    final E[] buffer = this.buffer;
    long currProducerIndexCache =
        lvProducerIndexCache(); // Volatile: inter-consumer cache coordination
    long currentConsumerIndex;
    long nextConsumerIndex =
        lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
    E e;
    do {
      currentConsumerIndex = nextConsumerIndex;
      if (currentConsumerIndex >= currProducerIndexCache) {
        long currProducerIndex = laProducerIndex(); // Acquire: pairs with producer's setRelease
        if (currentConsumerIndex >= currProducerIndex) {
          return null;
        } else {
          currProducerIndexCache = currProducerIndex;
          svProducerIndexCache(currProducerIndex); // Volatile: inter-consumer cache coordination
        }
      }
      e =
          laRefElement(
              buffer,
              arrayIndex(currentConsumerIndex)); // Acquire: pairs with producer's setRelease
      // sandwich the element load between 2 consumer index loads
      nextConsumerIndex =
          lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
    } while (null == e || nextConsumerIndex != currentConsumerIndex);
    return e;
  }

  /**
   * Relaxed offer without full guarantees.
   *
   * @param e element to offer (not null)
   * @return true if offered, false if full
   */
  @Override
  public boolean relaxedOffer(E e) {
    if (null == e) {
      throw new NullPointerException("Null is not a valid element");
    }
    final E[] buffer = this.buffer;
    final long producerIndex = lpProducerIndex(); // Plain: single producer, exclusive access
    final int producerArrayIndex = arrayIndex(producerIndex);
    if (null
        != laRefElement(
            buffer, producerArrayIndex)) { // Acquire: pairs with consumer's setRelease(null)
      return false;
    }
    soRefElement(buffer, producerArrayIndex, e); // Release: publish element to consumers
    // single producer, so store ordered is valid. It is also required to correctly publish the
    // element
    // and for the consumers to pick up the tail value.
    soProducerIndex(producerIndex + 1); // Release: publish tail advance to consumers
    return true;
  }

  /**
   * Relaxed poll, delegates to poll.
   *
   * @return head element or null if empty
   */
  @Override
  public E relaxedPoll() {
    return poll();
  }

  /**
   * Relaxed peek without full guarantees.
   *
   * @return head element or null if empty
   */
  @Override
  public E relaxedPeek() {
    final E[] buffer = this.buffer;
    final long mask = this.mask;
    long currentConsumerIndex;
    long nextConsumerIndex =
        lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
    E e;
    do {
      currentConsumerIndex = nextConsumerIndex;
      e =
          laRefElement(
              buffer,
              arrayIndex(currentConsumerIndex)); // Acquire: pairs with producer's setRelease
      // sandwich the element load between 2 consumer index loads
      nextConsumerIndex =
          lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
    } while (nextConsumerIndex != currentConsumerIndex);
    return e;
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
    if (null == c) throw new IllegalArgumentException("c is null");
    if (limit < 0) throw new IllegalArgumentException("limit is negative: " + limit);
    if (limit == 0) return 0;

    final E[] buffer = this.buffer;
    final long mask = this.mask;
    long currProducerIndexCache =
        lvProducerIndexCache(); // Volatile: inter-consumer cache coordination
    int adjustedLimit = 0;
    long currentConsumerIndex;
    do {
      currentConsumerIndex =
          lvConsumerIndex(); // Volatile: inter-consumer synchronization (needs StoreLoad)
      // is there any space in the queue?
      if (currentConsumerIndex >= currProducerIndexCache) {
        long currProducerIndex = laProducerIndex(); // Acquire: pairs with producer's setRelease
        if (currentConsumerIndex >= currProducerIndex) {
          return 0;
        } else {
          currProducerIndexCache = currProducerIndex;
          svProducerIndexCache(currProducerIndex); // Volatile: inter-consumer cache coordination
        }
      }
      // try and claim up to 'limit' elements in one go
      int remaining = (int) (currProducerIndexCache - currentConsumerIndex);
      adjustedLimit = Math.min(remaining, limit);
    } while (!casConsumerIndex(
        currentConsumerIndex,
        currentConsumerIndex + adjustedLimit)); // CAS: claim slots, full barrier

    for (int i = 0; i < adjustedLimit; i++) {
      c.accept(removeElement(buffer, currentConsumerIndex + i, mask));
    }
    return adjustedLimit;
  }

  /**
   * Fills queue with up to limit elements from supplier.
   *
   * @param s element supplier
   * @param limit max elements to fill
   * @return number of elements filled
   */
  @Override
  public int fill(final Supplier<E> s, final int limit) {
    if (null == s) {
      throw new IllegalArgumentException("supplier is null");
    }
    if (limit < 0) {
      throw new IllegalArgumentException("limit is negative:" + limit);
    }
    if (limit == 0) {
      return 0;
    }

    final E[] buffer = this.buffer;
    final long mask = this.mask;
    long producerIndex = this.lpProducerIndex(); // Plain: single producer, exclusive access

    for (int i = 0; i < limit; i++) {
      final int producerArrayIndex = arrayIndex(producerIndex);
      if (null
          != laRefElement(
              buffer, producerArrayIndex)) { // Acquire: pairs with consumer's setRelease(null)
        return i;
      }
      producerIndex++;
      soRefElement(buffer, producerArrayIndex, s.get()); // Release: publish element to consumers
      soProducerIndex(
          producerIndex); // Release: publish tail advance to consumers (ordered store -> atomic and
      // ordered for size())
    }
    return limit;
  }

  /**
   * Drains elements using wait strategy and exit condition.
   *
   * @param c element consumer
   * @param w wait strategy
   * @param exit exit condition
   */
  @Override
  public void drain(final Consumer<E> c, final WaitStrategy w, final ExitCondition exit) {
    MessagePassingQueueUtil.drain(this, c, w, exit);
  }

  /**
   * Fills queue using wait strategy and exit condition.
   *
   * @param s element supplier
   * @param w wait strategy
   * @param e exit condition
   */
  @Override
  public void fill(final Supplier<E> s, final WaitStrategy w, final ExitCondition e) {
    MessagePassingQueueUtil.fill(this, s, w, e);
  }
}
