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
package io.jaq.spsc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.util.UnsafeAccess;

abstract class FFBufferOfferBatchCqColdFields<E> extends ConcurrentRingBuffer<E> {
    protected ConcurrentQueueConsumer<E> consumer;
    protected ConcurrentQueueProducer<E> producer;

    public FFBufferOfferBatchCqColdFields(int capacity) {
        super(capacity);
    }
}

public final class FFBufferWithOfferBatchCq<E> extends FFBufferOfferBatchCqColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;


    static abstract class ProducerFields<E> extends ConcurrentRingBuffer<E> {
        protected static final long TAIL_OFFSET;
        static {
            try {
                TAIL_OFFSET = UnsafeAccess.UNSAFE
                        .objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        protected long tail;
        protected long batchTail;

        public ProducerFields(ConcurrentRingBuffer<E> c) {
            super(c);
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        public Producer(ConcurrentRingBuffer<E> c) {
            super(c);
        }

        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }

            E[] lb = buffer;
            if (tail >= batchTail) {
                if (null != loadVolatile(lb, offset(tail + OFFER_BATCH_SIZE))) {
                    return false;
                }
                batchTail = tail + OFFER_BATCH_SIZE;
            }
            storeOrdered(lb, offset(tail), e);
            tail++;

            return true;
        }

        private long getTailForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET), tail);
        }
    }

    static abstract class ConsumerFields<E> extends ConcurrentRingBuffer<E> {
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

        public ConsumerFields(ConcurrentRingBuffer<E> c) {
            super(c);
        }
    }

    static final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private Consumer(ConcurrentRingBuffer<E> c) {
            super(c);
        }

        @Override
        public E poll() {
            final long offset = offset(head);
            E[] lb = buffer;
            final E e = loadVolatile(lb, offset);
            if (null == e) {
                return null;
            }
            storeOrdered(lb, offset, null);
            head++;
            return e;
        }

        @Override
        public E peek() {
            return load(offset(head));
        }

        @Override
        public void clear() {
            while (null != poll())
                ;
        }

        private long getHeadForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET), head);
        }
    }

    public FFBufferWithOfferBatchCq(final int capacity) {
        super(capacity);
    }

    @Override
    public int size() {
        long headForSize = consumer == null ? 0 : ((Consumer<E>) consumer).getHeadForSize();
        long tailForSize = producer == null ? 0 : ((Producer<E>) producer).getTailForSize();
        return (int) (tailForSize - headForSize);
    }

    @Override
    public int capacity() {
        return capacity - OFFER_BATCH_SIZE;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        if (consumer == null) {
            consumer = new Consumer<>(this);
        }
        return consumer;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        if (producer == null) {
            producer = new Producer<>(this);
        }
        return producer;
    }
}
