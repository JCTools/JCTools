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

import static io.jaq.util.UnsafeAccess.UNSAFE;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.common.ConcurrentRingBuffer;

abstract class MpmcConcurrentQueueCqColdFields<E> extends ConcurrentRingBuffer<E> {

    private static abstract class ProducerFields<E> extends ConcurrentRingBuffer<E> {
        protected static final long TAIL_OFFSET;
        static {
            try {
                TAIL_OFFSET = UNSAFE.objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        private volatile long tail;
        private volatile long headCache;
        protected Consumer<E> consumer;

        public ProducerFields(MpmcConcurrentQueueCqColdFields<E> c) {
            super(c);
        }

        protected final long lvTail() {
            return tail;
        }

        protected final boolean casTail(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
        }

        protected final long lvHeadCache() {
            return headCache;
        }

        protected final void svHeadCache(long headCache) {
            this.headCache = headCache;
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

            final E[] lb = buffer;
            long currentHeadCache = lvHeadCache();
            long currentTail;
            do {
                currentTail = lvTail();
                final long wrapPoint = currentTail - capacity;
                if (currentHeadCache <= wrapPoint) {
                    currentHeadCache = consumer.lvHead();
                    if (currentHeadCache <= wrapPoint) {
                        return false;
                    } else {
                        // only store if value is useful
                        svHeadCache(currentHeadCache);
                    }
                }
            } while (!casTail(currentTail, currentTail + 1));

            final long offset = calcOffset(currentTail);
            // head may become visible before element is taken, verify slot is empty before storing
            while (lvElement(lb, offset) != null)
                ;
            soElement(lb, offset, e);
            return true;
        }
    }

    private static abstract class ConsumerFields<E> extends ConcurrentRingBuffer<E> {
        protected static final long HEAD_OFFSET;
        static {
            try {
                HEAD_OFFSET = UNSAFE.objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        private volatile long head = 0;
        private volatile long tailCache = 0;
        protected Producer<E> producer;

        public ConsumerFields(ConcurrentRingBuffer<E> c) {
            super(c);
        }

        protected final long lvHead() {
            return head;
        }

        protected final boolean casHead(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
        }

        protected long lvTailCache() {
            return tailCache;
        }

        protected void svTailCache(long tailCache) {
            this.tailCache = tailCache;
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
            final E[] lb = buffer;
            long currentTailCache = lvTailCache();
            long currentHead;
            do {
                currentHead = lvHead();
                if (currentHead >= currentTailCache) {
                    currentTailCache = producer.lvTail();
                    if (currentHead >= currentTailCache) {
                        return null;
                    } else {
                        // only store if value is useful
                        svTailCache(currentTailCache);
                    }
                }
            } while (!casHead(currentHead, currentHead + 1));

            final long offset = calcOffset(currentHead);
            E e;
            do {
                e = lvElement(lb, offset);
                // tail may become visible before element, verify slot is full before reading and nulling
            } while (e == null);
            soElement(lb, offset, null);
            return e;
        }

        @Override
        public E peek() {
            return lvElement(calcOffset(lvHead()));
        }

        @Override
        public void clear() {
            while (null != poll())
                ;
        }
    }

    protected final Consumer<E> consumer;
    protected final Producer<E> producer;

    public MpmcConcurrentQueueCqColdFields(int capacity) {
        super(capacity);
        consumer = new Consumer<>(this);
        producer = new Producer<>(this);
        producer.consumer = consumer;
        consumer.producer = producer;
    }
}

public final class MpmcConcurrentQueueCq<E> extends MpmcConcurrentQueueCqColdFields<E> implements
        ConcurrentQueue<E> {
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpmcConcurrentQueueCq(final int capacity) {
        super(capacity);
    }

    @Override
    public int size() {
        return (int) (((Producer<E>) producer).lvTail() - ((Consumer<E>) consumer).lvHead());
    }

    @Override
    public int capacity() {
        return capacity;
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
