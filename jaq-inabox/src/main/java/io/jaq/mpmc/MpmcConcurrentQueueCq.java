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
import io.jaq.common.ConcurrentSequencedRingBuffer;

abstract class MpmcConcurrentQueueCqColdFields<E> extends ConcurrentSequencedRingBuffer<E> {

    private static abstract class ProducerFields<E> extends ConcurrentSequencedRingBuffer<E> {
        protected static final long TAIL_OFFSET;
        static {
            try {
                TAIL_OFFSET = UNSAFE.objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        private volatile long tail;

        public ProducerFields(ConcurrentSequencedRingBuffer<E> c) {
            super(c);
        }

        protected final long lvTail() {
            return tail;
        }

        protected final boolean casTail(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        public Producer(ConcurrentSequencedRingBuffer<E> c) {
            super(c);
        }

        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }
            final long[] lsb = sequenceBuffer;
            long currentTail;
            long pOffset;

            for (;;) {
                currentTail = lvTail();
                pOffset = calcSequenceOffset(currentTail);
                long seq = lvSequenceElement(lsb, pOffset);
                long delta = seq - currentTail;
                if (delta == 0) {
                    // this is expected if we see this first time around
                    if (casTail(currentTail, currentTail + 1)) {
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // poll has not moved this value forward,
                    return false;
                } else {

                }
            }
            long offset = calcOffset(currentTail);
            spElement(offset, e);
            // increment position, seeing this value again should lead to retry 2
            soSequenceElement(lsb, pOffset, currentTail + 1);
            return true;
        }
    }

    private static abstract class ConsumerFields<E> extends ConcurrentSequencedRingBuffer<E> {
        protected static final long HEAD_OFFSET;
        static {
            try {
                HEAD_OFFSET = UNSAFE.objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        private volatile long head = 0;

        public ConsumerFields(ConcurrentSequencedRingBuffer<E> c) {
            super(c);
        }

        protected final long lvHead() {
            return head;
        }

        protected final boolean casHead(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, HEAD_OFFSET, expect, newValue);
        }
    }

    static final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private Consumer(ConcurrentSequencedRingBuffer<E> c) {
            super(c);
        }

        @Override
        public E poll() {
            final long[] lsb = sequenceBuffer;
            long currentHead;
            long pOffset;
            for (;;) {
                currentHead = lvHead();
                pOffset = calcSequenceOffset(currentHead);
                long seq = lvSequenceElement(lsb, pOffset);
                long delta = seq - (currentHead + 1);
                if (delta == 0) {
                    if (casHead(currentHead, currentHead + 1)) {
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    return null;
                } else {

                }
            }
            long offset = calcOffset(currentHead);
            final E[] lb = buffer;
            E e = lvElement(lb, offset);
            spElement(lb, offset, null);
            soSequenceElement(lsb, pOffset, currentHead + capacity);
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
    }
}

public final class MpmcConcurrentQueueCq<E> extends MpmcConcurrentQueueCqColdFields<E> implements
        ConcurrentQueue<E> {
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpmcConcurrentQueueCq(final int capacity) {
        super(Math.max(2, capacity));
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
