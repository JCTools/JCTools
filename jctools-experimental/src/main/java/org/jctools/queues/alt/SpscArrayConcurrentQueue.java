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
package org.jctools.queues.alt;

import static org.jctools.queues.alt.SpscArrayConcurrentQueue.OFFER_BATCH_SIZE;
import static org.jctools.util.UnsafeAccess.UNSAFE;

import org.jctools.util.UnsafeAccess;

abstract class ProducerFields<E> extends ConcurrentCircularArray<E> {
    protected static final long TAIL_OFFSET;
    static {
        try {
            TAIL_OFFSET = UnsafeAccess.UNSAFE.objectFieldOffset(ProducerFields.class
                    .getDeclaredField("producerIndex"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long producerIndex;
    protected long batchTail;

    public ProducerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public Producer(ConcurrentCircularArray<E> c) {
        super(c);
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        final E[] lb = buffer;
        final long mask = this.mask;
        final long pIndex = producerIndex;
        if (pIndex >= batchTail) {
            if (null == lvElement(lb, calcOffset(pIndex + OFFER_BATCH_SIZE, mask))) {
                batchTail = pIndex + OFFER_BATCH_SIZE;
            } else if (null != lvElement(lb, calcOffset(pIndex, mask))) {
                return false;
            }
        }
        soElement(lb, calcOffset(pIndex, mask), e);
        soProducerIndex(pIndex + 1);

        return true;
    }

    long getProducerIndexForSize() {
        return Math.max(lvProducerIndex(), producerIndex);
    }

    private long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, TAIL_OFFSET);
    }

    private void soProducerIndex(long newHead) {
        UNSAFE.putOrderedLong(this, TAIL_OFFSET, newHead);
    }

    @Override
    public boolean weakOffer(E e) {
        return offer(e);
    }

    @Override
    public int produce(ProducerFunction<E> p, int batchSize) {
        final E[] lb = buffer;
        final long mask = this.mask;
        long pIndex = producerIndex;
        final long tIndex = pIndex + batchSize - 1;
        
        if (tIndex >= batchTail) {
            if (null == lvElement(lb, calcOffset(tIndex + OFFER_BATCH_SIZE, mask))) {
                batchTail = tIndex + OFFER_BATCH_SIZE;
            } else {
                for(int i=0;i<batchSize;i++) {
                    if (null != lvElement(lb, calcOffset(pIndex, mask)))
                        return i;
                    soElement(lb, calcOffset(pIndex, mask), p.produce());
                    soProducerIndex(++pIndex);
                }
                return batchSize;
            }
        }
        soProducerIndex(pIndex + batchSize);
        for(int i=0;i<batchSize;i++) {
            soElement(lb, calcOffset(pIndex++, mask), p.produce());
        }
        return batchSize;
    }
}

abstract class ConsumerFields<E> extends ConcurrentCircularArray<E> {
    protected static final long HEAD_OFFSET;
    static {
        try {
            HEAD_OFFSET = UnsafeAccess.UNSAFE
                    .objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected long head = 0;

    public ConsumerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    Consumer(ConcurrentCircularArray<E> c) {
        super(c);
    }

    @Override
    public E poll() {
        final long head = this.head;
        final long offset = calcOffset(head);
        final E[] lb = buffer;
        final E e = lvElement(lb, offset);
        if (null == e) {
            return null;
        }
        soElement(lb, offset, null);
        soHead(head + 1);
        return e;
    }

    @Override
    public E peek() {
        return lpElement(calcOffset(head));
    }

    @Override
    public void clear() {
        while (null != poll())
            ;
    }

    long getHeadForSize() {
        return Math.max(lvHead(), head);
    }

    private long lvHead() {
        return UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }

    private void soHead(long newHead) {
        UNSAFE.putOrderedLong(this, HEAD_OFFSET, newHead);
    }

    @Override
    public int consume(ConsumerFunction<E> c, int batch) {
        final E[] lb = buffer;
        long currHead = head;
        long offset = calcOffset(currHead);
        E e = lvElement(lb, offset);
        int i = 0;
        for (; i < batch && null != e; i++) {
            soElement(lb, offset, null);
            soHead(++currHead);
            c.consume(e); // NOTE: we've committed to consuming the batch, no check.
            offset = calcOffset(currHead);
            e = lvElement(lb, offset);
        }
        return i;
    }

    @Override
    public E weakPoll() {
        return poll();
    }

    @Override
    public E weakPeek() {
        return peek();
    }
}

abstract class SpscArrayConcurrentQueueColdFields<E> extends ConcurrentCircularArray<E> {
    protected final Consumer<E> consumer;
    protected final Producer<E> producer;

    public SpscArrayConcurrentQueueColdFields(int capacity) {
        super(capacity);
        consumer = new Consumer<>(this);
        producer = new Producer<>(this);
    }
}

public final class SpscArrayConcurrentQueue<E> extends SpscArrayConcurrentQueueColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public SpscArrayConcurrentQueue(final int capacity) {
        super(Math.max(capacity, 2 * OFFER_BATCH_SIZE));
    }

    @Override
    public int size() {
        long headForSize = consumer.getHeadForSize();
        long tailForSize = producer.getProducerIndexForSize();
        return (int) (tailForSize - headForSize);
    }

    @Override
    public int capacity() {
        return (int) mask + 1;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return consumer;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return producer;
    }
}
