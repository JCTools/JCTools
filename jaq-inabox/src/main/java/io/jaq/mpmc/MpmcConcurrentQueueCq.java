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
package io.jaq.mpmc;

import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.spsc.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

abstract class MpmcConcurrentQueueCqColdFields<E> extends ConcurrentRingBuffer<E> {
    protected ConcurrentQueueConsumer<E> consumer;
    protected ConcurrentQueueProducer<E> producer;

    public MpmcConcurrentQueueCqColdFields(int capacity) {
        super(capacity);
    }
}

public final class MpmcConcurrentQueueCq<E> extends MpmcConcurrentQueueCqColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    protected static final int OFFER_BATCH_SIZE = Integer.getInteger("offer.batch.size", 4096);
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;


    private static abstract class ProducerFields<E> extends ConcurrentRingBuffer<E> {
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
        protected long headCache;
        protected Consumer<E> consumer;
        
        public ProducerFields(MpmcConcurrentQueueCqColdFields<E> c) {
            super(c);
        }
        
        final long vlTail() {
            return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
        }

        final boolean casTail(long expect, long newValue) {
            return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
        }

        final long getTailForSize() {
            return Math.max(vlTail(), tail);
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        public Producer(MpmcConcurrentQueueCqColdFields<E> c) {
            super(c);
        }

        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }

            E[] lb = buffer;

            long currentTail;
            do {
                currentTail = vlTail();
                final long wrapPoint = currentTail - capacity;
                if (headCache <= wrapPoint) {
                    headCache = consumer.vlHead();
                    if (headCache <= wrapPoint) {
                        return false;
                    }
                    else {
                        // head may become visible before element is taken
                        while (loadVolatile(lb, offset(headCache-1)) != null);
                        // now the coast is clear :)
                    }
                }
            } while (!casTail(currentTail, currentTail + 1));
            final long offset = offset(currentTail);
            storeOrdered(lb, offset, e);
            return true;
        }
    }

    private static abstract class ConsumerFields<E> extends ConcurrentRingBuffer<E> {
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
        protected long tailCache = 0;
        protected Producer<E> producer;
        public ConsumerFields(ConcurrentRingBuffer<E> c) {
            super(c);
        }
        final long vlHead() {
            return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
        }
        final boolean casHead(long expect, long newValue) {
            return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
        }

        final long getHeadForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET), head);
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
            long currentHead;
            E e;
            final E[] lb = buffer;
            do {
                currentHead = vlHead();
                if (currentHead >= tailCache) {
                    tailCache = producer.vlTail();
                    if (currentHead >= tailCache) {
                        return null;
                    }
                    else {
                        // tail may become visible before element
                        do {
                            e = loadVolatile(lb, offset(tailCache-1));
                        } while (e == null);
                    }
                }
            } while (!casHead(currentHead, currentHead + 1));
            // tail may become visible before element
            final long offset = offset(currentHead);
            e = loadVolatile(lb, offset);
            storeOrdered(lb, offset, null);
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
    }

    public MpmcConcurrentQueueCq(final int capacity) {
        super(capacity);
        consumer = new Consumer<>(this);
        producer = new Producer<>(this);
        ((Producer<E>)producer).consumer = (Consumer<E>) consumer;
        ((Consumer<E>)consumer).producer = (Producer<E>) producer;
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
        return consumer;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return producer;
    }
}
