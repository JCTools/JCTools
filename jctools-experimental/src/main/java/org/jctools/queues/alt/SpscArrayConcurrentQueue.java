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
import static org.jctools.util.UnsafeAccess.fieldOffset;

import org.jctools.util.UnsafeAccess;

abstract class ProducerFields<E> extends ConcurrentCircularArray<E> {
    protected static final long TAIL_OFFSET = fieldOffset(ProducerFields.class,"producerIndex");

    protected long producerIndex;
    protected long batchTail;

    public ProducerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
    protected static final long HEAD_OFFSET = fieldOffset(ConsumerFields.class, "head");

    protected long head = 0;

    public ConsumerFields(ConcurrentCircularArray<E> c) {
        super(c);
    }
}

final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
        consumer = new Consumer<E>(this);
        producer = new Producer<E>(this);
    }
}

public final class SpscArrayConcurrentQueue<E> extends SpscArrayConcurrentQueueColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    // post pad queue fields
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

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
