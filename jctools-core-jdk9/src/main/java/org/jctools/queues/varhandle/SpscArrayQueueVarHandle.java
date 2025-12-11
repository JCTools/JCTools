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

import java.util.Objects;

/**
 * Single-Producer, Single-Consumer bounded queue using a circular buffer. Uses cached indices to
 * eliminate redundant volatile reads and look-ahead optimization to batch producer index updates.
 *
 * @param <E> the type of elements held in this queue
 * @author <a href="https://github.com/amarziali">Andrea Marziali</a>
 */
public final class SpscArrayQueueVarHandle<E> extends BaseQueue<E> {
  private final int lookAheadStep;
  protected long producerLimit;

  /**
   * @param requestedCapacity queue capacity (rounded up to power of two)
   */
  public SpscArrayQueueVarHandle(int requestedCapacity) {
    super(requestedCapacity);
    // This should go in a common place because today is defined under jctools-channel
    // but here I'd like not to draw that dependency
    lookAheadStep =
        Math.min(capacity / 4, Integer.getInteger("jctools.spsc.max.lookahead.step", 4096));
  }

  /**
   * Offers element to queue.
   *
   * @param e element to add (not null)
   * @return true if added, false if full
   */
  @Override
  public boolean offer(E e) {
    Objects.requireNonNull(e);
    // local load of field to avoid repeated loads after volatile reads
    final E[] buffer = this.buffer;
    final long producerIndex = this.lpProducerIndex();

    if (producerIndex >= producerLimit && !offerSlowPath(buffer, producerIndex)) {
      return false;
    }
    soRefElement(buffer, arrayIndex(producerIndex), e); // Release: publish element to consumer
    soProducerIndex(producerIndex + 1); // Release: publish tail advance after element write

    return true;
  }

  private boolean offerSlowPath(final E[] buffer, final long producerIndex) {
    final int lookAheadStep = this.lookAheadStep;
    if (null == lvRefElement(buffer, arrayIndex(producerIndex + lookAheadStep))) {
      producerLimit = producerIndex + lookAheadStep;
    } else {
      return null == lvRefElement(buffer, arrayIndex(producerIndex));
    }
    return true;
  }

  /**
   * Polls element from queue.
   *
   * @return head element or null if empty
   */
  @Override
  public E poll() {
    final long currentHead = lpConsumerIndex(); // Plain: single consumer, no contention
    final int index = arrayIndex(currentHead);

    final E value =
        lvRefElement(buffer, index); // Volatile: ensure element visibility from producer
    if (null == value) {
      return null;
    }
    soRefElement(buffer, index, null); // Release: publish slot clear to producer
    soConsumerIndex(currentHead + 1); // Release: publish head advance after slot clear

    return value;
  }

  /**
   * Peeks at head element.
   *
   * @return head element or null if empty
   */
  @Override
  public E peek() {
    return lvRefElement(buffer, arrayIndex(lpConsumerIndex()));
  }

  /**
   * Relaxed offer, delegates to offer.
   *
   * @param message element to offer
   * @return true if added, false if full
   */
  @Override
  public boolean relaxedOffer(final E message) {
    return offer(message);
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
   * Relaxed peek, delegates to peek.
   *
   * @return head element or null if empty
   */
  @Override
  public E relaxedPeek() {
    return peek();
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
    final long mask = this.mask;
    final long consumerIndex = this.lpConsumerIndex();

    for (int i = 0; i < limit; i++) {
      final long index = consumerIndex + i;
      final int arrayIndex = arrayIndex(index);
      final E e = lvRefElement(buffer, arrayIndex);
      if (null == e) {
        return i;
      }
      soRefElement(buffer, arrayIndex, null);
      soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
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
    final int lookAheadStep = this.lookAheadStep;
    final long producerIndex = this.lpProducerIndex();

    for (int i = 0; i < limit; i++) {
      final long index = producerIndex + i;
      final int lookAheadElementIndex = arrayIndex(index + lookAheadStep);
      if (null == lvRefElement(buffer, lookAheadElementIndex)) {
        int lookAheadLimit = Math.min(lookAheadStep, limit - i);
        for (int j = 0; j < lookAheadLimit; j++) {
          final int currentIndex = arrayIndex(index + j);
          soRefElement(buffer, currentIndex, s.get());
          soProducerIndex(index + j + 1); // ordered store -> atomic and ordered for size()
        }
        i += lookAheadLimit - 1;
      } else {
        final int currentIndex = arrayIndex(index);
        if (null != lvRefElement(buffer, currentIndex)) {
          return i;
        }
        soRefElement(buffer, currentIndex, s.get());
        soProducerIndex(index + 1); // ordered store -> atomic and ordered for size()
      }
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
    if (null == c) {
      throw new IllegalArgumentException("c is null");
    }
    if (null == w) {
      throw new IllegalArgumentException("wait is null");
    }
    if (null == exit) {
      throw new IllegalArgumentException("exit condition is null");
    }

    final E[] buffer = this.buffer;
    long consumerIndex = this.lpConsumerIndex();

    int counter = 0;
    while (exit.keepRunning()) {
      for (int i = 0; i < 4096; i++) {
        final int arrayConsumerIndex = arrayIndex(consumerIndex);
        final E e = lvRefElement(buffer, arrayConsumerIndex);
        if (null == e) {
          counter = w.idle(counter);
          continue;
        }
        consumerIndex++;
        counter = 0;
        soRefElement(buffer, arrayConsumerIndex, null);
        soConsumerIndex(consumerIndex); // ordered store -> atomic and ordered for size()
        c.accept(e);
      }
    }
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
    if (null == w) throw new IllegalArgumentException("waiter is null");
    if (null == e) throw new IllegalArgumentException("exit condition is null");
    if (null == s) throw new IllegalArgumentException("supplier is null");

    final E[] buffer = this.buffer;
    final long mask = this.mask;
    final int lookAheadStep = this.lookAheadStep;
    long producerIndex = this.lpProducerIndex();
    int counter = 0;
    while (e.keepRunning()) {
      final int lookAheadElementIndex = arrayIndex(producerIndex + lookAheadStep);
      if (null == lvRefElement(buffer, lookAheadElementIndex)) {
        for (int j = 0; j < lookAheadStep; j++) {
          final int arrayProducerIndex = arrayIndex(producerIndex);
          producerIndex++;
          soRefElement(buffer, arrayProducerIndex, s.get());
          soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
        }
      } else {
        final int arrayProducerIndex = arrayIndex(producerIndex);
        if (null != lvRefElement(buffer, arrayProducerIndex)) {
          counter = w.idle(counter);
          continue;
        }
        producerIndex++;
        counter = 0;
        soRefElement(buffer, arrayProducerIndex, s.get());
        soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
      }
    }
  }
}
