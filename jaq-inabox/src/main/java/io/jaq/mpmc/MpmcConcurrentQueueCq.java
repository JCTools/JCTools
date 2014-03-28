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
import io.jaq.common.ConcurrentRingBuffer;
import io.jaq.util.UnsafeAccess;

abstract class MpmcConcurrentQueueCqColdFields<E> extends ConcurrentRingBuffer<E> {

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
            return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
        }

        protected long lvHeadCache() {
            return headCache;
        }

        protected void svHeadCache(long headCache) {
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

            E[] lb = buffer;

            long currentTail;
            long lHeadCache = lvHeadCache();
            do {
                currentTail = lvTail();
                final long wrapPoint = currentTail - capacity;
                if (lHeadCache <= wrapPoint) {
                    lHeadCache = consumer.lvHead();
                    if (lHeadCache <= wrapPoint) {
                        return false;
                    } else {
                        svHeadCache(lHeadCache);
                    }
                }
            } while (!casTail(currentTail, currentTail + 1));

            final long offset = offset(currentTail);
            // head may become visible before element is taken
            while (loadVolatile(lb, offset) != null)
                ;
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
            return UnsafeAccess.UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
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
            long currentHead;
            E e;
            final E[] lb = buffer;
            long lTailCache = lvTailCache();
            do {
                currentHead = lvHead();

                if (currentHead >= lTailCache) {
                    lTailCache = producer.lvTail();
                    if (currentHead >= lTailCache) {
                        return null;
                    } else {
                        svTailCache(lTailCache);
                    }
                }
            } while (!casHead(currentHead, currentHead + 1));
            // tail may become visible before element
            final long offset = offset(currentHead);
            do {
                e = loadVolatile(lb, offset);
            } while (e == null);
            storeOrdered(lb, offset, null);
            return e;
        }

        @Override
        public E peek() {
            return load(offset(lvHead()));
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
