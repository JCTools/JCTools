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
package io.jaq.mpsc;

import io.jaq.BatchConsumer;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;
import io.jaq.util.Pow2;
import io.jaq.util.UnsafeAccess;

abstract class MpscConcurrentFFQueueL0Pad {
    public long p00, p01, p02, p03, p04, p05, p06, p07;
    public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscConcurrentFFQueueColdFields<E> extends MpscConcurrentFFQueueL0Pad {
    protected static final int BUFFER_PAD = 32;
    protected static final int SPARSE_SHIFT = Integer.getInteger("sparse.shift", 0);
    protected final int capacity;
    protected final long mask;
    protected final E[] buffer;

    @SuppressWarnings("unchecked")
    public MpscConcurrentFFQueueColdFields(int capacity) {
        if (Pow2.isPowerOf2(capacity)) {
            this.capacity = capacity;
        } else {
            this.capacity = Pow2.findNextPositivePowerOfTwo(capacity);
        }
        mask = this.capacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[(this.capacity << SPARSE_SHIFT) + BUFFER_PAD * 2];
    }
}

public final class MpscConcurrentFFQueue<E> extends MpscConcurrentFFQueueColdFields<E> implements
        ConcurrentQueue<E> {
    // Layout field/data offsets are runtime constants
    private static final long TAIL_OFFSET;
    private static final long HEAD_OFFSET;
    private static final long ARRAY_BASE;
    private static final int ELEMENT_SHIFT;
    static {
        try {
            TAIL_OFFSET =
                    UnsafeAccess.UNSAFE.objectFieldOffset(ProducerFields.class.getDeclaredField("tail"));
            HEAD_OFFSET =
                    UnsafeAccess.UNSAFE.objectFieldOffset(ConsumerFields.class.getDeclaredField("head"));
            final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
            if (4 == scale) {
                ELEMENT_SHIFT = 2 + SPARSE_SHIFT;
            } else if (8 == scale) {
                ELEMENT_SHIFT = 3 + SPARSE_SHIFT;
            } else {
                throw new IllegalStateException("Unknown pointer size");
            }
            // Including the buffer pad in the array base offset
            ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class)
                    + (BUFFER_PAD << (ELEMENT_SHIFT - SPARSE_SHIFT));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    static abstract class ProducerFieldsPad0 {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;
    }

    static abstract class ProducerFields<E> extends ProducerFieldsPad0 {
        protected final E[] buffer;
        protected final long mask;
        protected long tail;
        protected long batchTail;

        public ProducerFields(E[] buffer, long mask) {
            this.buffer = buffer;
            this.mask = mask;
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        public Producer(E[] buffer, long mask) {
            super(buffer, mask);
        }

        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private long offset(long index) {
            return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
        }
        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }

            long currentTail;
            do {
                currentTail = getTail();
//                final long wrapPoint = currentTail - capacity;
//                if (getHeadV() <= wrapPoint) { // volatile read of head
//                    return false;
//                }
            } while (!casTail(currentTail, currentTail + 1));
            UnsafeAccess.UNSAFE.putOrderedObject(buffer, offset(currentTail), e);
            return true;
        }

        @Override
        public boolean offer(E[] ea) {
            return false;
        }
        private long getTailForSize() {
            return Math.max(UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET), tail);
        }
        private long getTail() {
            return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
        }

        private boolean casTail(long expect, long newValue) {
            return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
        }
    }

    static abstract class ConsumerFieldsPad0<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;
    }

    static abstract class ConsumerFields<E> extends ConsumerFieldsPad0<E> {
        protected final long mask;
        protected final E[] buffer;
        protected long head = 0;

        public ConsumerFields(E[] buffer, long mask) {
            this.buffer = buffer;
            this.mask = mask;
        }
    }

    static final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private Consumer(E[] buffer, long mask) {
            super(buffer, mask);
        }

        private long offset(long index) {
            return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
        }
        @Override
        public E poll() {
            final long offset = offset(head);
            @SuppressWarnings("unchecked")
            final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(buffer, offset);
            if (null == e) {
                return null;
            }
            UnsafeAccess.UNSAFE.putObject(buffer, offset, null);
            lazySetHead(head + 1);
            return e;
        }
        private long getHeadV() {
            return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
        }

        private void lazySetHead(long l) {
            UnsafeAccess.UNSAFE.putOrderedLong(this, HEAD_OFFSET, l);
        }

        @Override
        @SuppressWarnings("unchecked")
        public E peek() {
            return (E) UnsafeAccess.UNSAFE.getObject(buffer, offset(head));
        }

        @Override
        public void consumeBatch(BatchConsumer<E> bc, int batchSizeLimit) {
            // TODO: can optimize for single consumer here
            boolean last;
            do {
                E e = poll();
                if (e == null) {
                    return;
                }
                last = (peek() == null) || --batchSizeLimit == 0;
                bc.consume(e, last);
            } while (!last);
        }

        @Override
        public void clear() {
            while (poll() != null) {
            }
        }
    }

    public MpscConcurrentFFQueue(final int capacity) {
        super(capacity);
    }


    @Override
    public int size() {
        return 0;
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return new Consumer<E>(buffer, mask);
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return new Producer<E>(buffer, mask);
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
