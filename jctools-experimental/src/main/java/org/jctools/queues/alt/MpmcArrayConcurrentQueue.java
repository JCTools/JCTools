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

import static org.jctools.util.UnsafeAccess.UNSAFE;

abstract class MpmcArrayConcurrentQueueColdFields<E> extends ConcurrentSequencedCircularArray<E> {

    private static abstract class ProducerFields<E> extends ConcurrentSequencedCircularArray<E> {
        protected static final long P_INDEX_OFFSET;
        static {
            try {
                P_INDEX_OFFSET = UNSAFE.objectFieldOffset(ProducerFields.class
                        .getDeclaredField("producerIndex"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        protected Consumer<E> consumer;
        private volatile long producerIndex;

        public ProducerFields(ConcurrentSequencedCircularArray<E> c) {
            super(c);
        }

        protected final long lvProducerIndex() {
            return producerIndex;
        }

        protected final boolean casProducerIndex(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
        }
    }

    static final class Producer<E> extends ProducerFields<E> implements ConcurrentQueueProducer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        public Producer(ConcurrentSequencedCircularArray<E> c) {
            super(c);
        }

        @Override
        public boolean offer(final E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }

            // local load of field to avoid repeated loads after volatile reads
            final long mask = this.mask;
            final long capacity = mask + 1;
            final long[] sBuffer = sequenceBuffer;
            long currIndex;
            long sOffset;
            long cIndex = Long.MAX_VALUE;// start with bogus value, hope we don't need it
            while (true) {
                currIndex = lvProducerIndex(); // LoadLoad
                sOffset = calcSequenceOffset(currIndex, mask);
                final long delta = lvSequence(sBuffer, sOffset) - currIndex;

                if (delta == 0) {
                    // this is expected if we see this first time around
                    if (casProducerIndex(currIndex, currIndex + 1)) {
                        // Successful CAS: full barrier
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0 && // poll has not moved this value forward
                        currIndex - capacity <= cIndex && // test against cached cIndex
                        currIndex - capacity <= (cIndex = consumer.lvConsumerIndex())) { // test against latest
                    // Extra check required to ensure [Queue.offer == false iff queue is full]
                    return false;
                }
                // another producer has moved the sequence by one, retry 2
            }
            addElement(e, mask, sBuffer, currIndex, sOffset);

            return true;
        }

        private void addElement(final E e, final long mask, final long[] sBuffer, long index, long sOffset) {
            // on 64bit(no compressed oops) JVM this is the same as seqOffset
            final long elementOffset = calcOffset(index, mask);
            spElement(elementOffset, e);

            // increment sequence by 1, the value expected by consumer
            // (seeing this value from a producer will lead to retry 2)
            soSequence(sBuffer, sOffset, index + 1); // StoreStore
        }

        @Override
        public boolean weakOffer(E e) {
            if (null == e) {
                throw new NullPointerException("Null is not a valid element");
            }
            final long[] lsb = sequenceBuffer;
            final long mask = this.mask;

            long currIndex;
            long sOffset;

            for (;;) {
                currIndex = lvProducerIndex();
                sOffset = calcSequenceOffset(currIndex, mask);
                long delta = lvSequence(lsb, sOffset) - currIndex;
                if (delta == 0) {
                    // this is expected if we see this first time around
                    if (casProducerIndex(currIndex, currIndex + 1)) {
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // poll has not moved this value forward, but that doesn't mean
                    // queue is empty.
                    return false;
                } else {

                }
            }
            addElement(e, mask, lsb, currIndex, sOffset);
            return true;
        }

        @Override
        public int produce(ProducerFunction<E> producer) {
            E e;
            int i = 0;
            while ((e = producer.produce()) != null && weakOffer(e)) {
                i++;
            }
            return i;
        }

        @Override
        public int produce(ProducerFunction<E> producer, int batchSize) {
            E e;
            int i = 0;
            for (; i < batchSize; i++) {
                e = producer.produce();
                assert e != null;
                if (!weakOffer(e)) {
                    break;
                }
            }
            return i;
        }
    }

    private static abstract class ConsumerFields<E> extends ConcurrentSequencedCircularArray<E> {
        protected static final long C_INDEX_OFFSET;
        static {
            try {
                C_INDEX_OFFSET = UNSAFE.objectFieldOffset(ConsumerFields.class
                        .getDeclaredField("consumerIndex"));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        private volatile long consumerIndex = 0;
        protected Producer<E> producer;

        public ConsumerFields(ConcurrentSequencedCircularArray<E> c) {
            super(c);
        }

        protected final long lvConsumerIndex() {
            return consumerIndex;
        }

        protected final boolean casConsumerIndex(long expect, long newValue) {
            return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
        }
    }

    static final class Consumer<E> extends ConsumerFields<E> implements ConcurrentQueueConsumer<E> {
        long p00, p01, p02, p03, p04, p05, p06, p07;
        long p10, p11, p12, p13, p14, p15, p16, p17;

        private Consumer(ConcurrentSequencedCircularArray<E> c) {
            super(c);
        }

        @Override
        public E weakPoll() {
            // local load of field to avoid repeated loads after volatile reads
            final long[] lSequenceBuffer = sequenceBuffer;
            final long mask = this.mask;
            return weakPoll(lSequenceBuffer, mask);
        }

        private E weakPoll(final long[] sBuffer, final long mask) {
            long currIndex;
            long sOffset;
            while (true) {
                currIndex = lvConsumerIndex();// LoadLoad
                sOffset = calcSequenceOffset(currIndex, mask);
                final long seq = lvSequence(sBuffer, sOffset);// LoadLoad
                final long delta = seq - (currIndex + 1);

                if (delta == 0) {
                    if (casConsumerIndex(currIndex, currIndex + 1)) {
                        // Successful CAS: full barrier
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // nothing here, but queue might not be empty
                    return null;
                }

                // another consumer beat us and moved sequence ahead, retry 2
            }

            return consumeElement(sBuffer, buffer, mask, currIndex, sOffset);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Because return null indicates queue is empty we cannot simply rely on next element visibility for
         * poll and must test producer index when next element is not visible.
         */
        @Override
        public E poll() {
            // local load of field to avoid repeated loads after volatile reads
            final long[] lSequenceBuffer = sequenceBuffer;
            final long mask = this.mask;
            long currentConsumerIndex;
            long seqOffset;
            long pIndex = -1; // start with bogus value, hope we don't need it
            while (true) {
                currentConsumerIndex = lvConsumerIndex();// LoadLoad
                seqOffset = calcSequenceOffset(currentConsumerIndex, mask);
                final long seq = lvSequence(lSequenceBuffer, seqOffset);// LoadLoad
                final long delta = seq - (currentConsumerIndex + 1);

                if (delta == 0) {
                    if (casConsumerIndex(currentConsumerIndex, currentConsumerIndex + 1)) {
                        // Successful CAS: full barrier
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0 && // slot has not been moved by producer
                        currentConsumerIndex >= pIndex && // test against cached pIndex
                        currentConsumerIndex == (pIndex = producer.lvProducerIndex())) { // update pIndex if
                                                                                         // we must
                    // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                    return null;
                }

                // another consumer beat us and moved sequence ahead, retry 2
            }

            return consumeElement(lSequenceBuffer, buffer, mask, currentConsumerIndex);
        }

        @Override
        public E peek() {
            long currConsumerIndex;
            E e;
            do {
                currConsumerIndex = lvConsumerIndex();
                // other consumers may have grabbed the element, or queue might be empty
                e = lpElement(calcOffset(currConsumerIndex));
                // only return null if queue is empty
            } while (e == null && currConsumerIndex != producer.lvProducerIndex());
            return e;
        }

        @Override
        public void clear() {
            while (null != poll())
                ;
        }

        @Override
        public int consume(ConsumerFunction<E> consumer) {
            final long[] sBuffer = this.sequenceBuffer;
            final E[] eBuffer = this.buffer;
            final long mask = this.mask;
            int i = 0;
            while (i < mask + 1) {
                final long currIndex = lvConsumerIndex();// LoadLoad
                final long sOffset = calcSequenceOffset(currIndex, mask);
                final long currSequence = lvSequence(sBuffer, sOffset);// LoadLoad
                final long delta = currSequence - (currIndex + 1);
                if (delta == 0) {
                    if (casConsumerIndex(currIndex, currIndex + 1)) {
                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final E e = consumeElement(sBuffer, eBuffer, mask, currIndex, sOffset);
                        i++;
                        if (!consumer.consume(e)) {
                            break;
                        }
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // nothing here, but queue might not be empty
                    break;
                }
                // another consumer beat us and moved sequence ahead, retry 2
            }
            return i;
        }

        private E consumeElement(long[] sBuffer, E[] eBuffer, long mask, long currIndex, long sOffset) {
            final long eOffset = calcOffset(currIndex, mask);
            final E e = lpElement(eOffset);
            spElement(eBuffer, eOffset, null);

            // Move sequence ahead by capacity, preparing it for next offer
            // (seeing this value from a consumer will lead to retry 2)
            soSequence(sBuffer, sOffset, currIndex + mask + 1);// StoreStore
            return e;
        }
        private E consumeElement(long[] sBuffer, E[] eBuffer, long mask, long currIndex) {
            return consumeElement(sBuffer, eBuffer, mask, currIndex, calcSequenceOffset(currIndex, mask));
        }
        @Override
        public int consume(ConsumerFunction<E> consumer, int batchSize) {
            final long[] sBuffer = this.sequenceBuffer;
            final E[] eBuffer = this.buffer;
            final long mask = this.mask;
            int i = 0;

            while (true) {
                long currIndex = lvConsumerIndex();// LoadLoad
                final long target = currIndex + batchSize;
                long sOffset = calcSequenceOffset(target, mask);
                final long currSequence = lvSequence(sBuffer, sOffset);// LoadLoad
                final long delta = currSequence - target;
                if (delta == 0) {
                    if (casConsumerIndex(currIndex, target)) {
                        // can now consume all elements
                        for (;i<batchSize;i++) {
                            E e = consumeElement(sBuffer, eBuffer, mask, currIndex++);
                            consumer.consume(e);
                        }
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // bugger, queue is either:
                    // (a) empty/we are unlucky and see a 'bubble', we don't make that distinction here. 
                    // (b) has less than batchSize elements
                    // we take the easy way out and weakPoll through this case
                    for (;i<batchSize;i++) {
                        final E e = weakPoll();
                        if(e == null) {
                            break;
                        }
                        consumer.consume(e);
                    }
                    break;
                }
                // another consumer beat us and moved sequence ahead, retry 2
            }
            return i;
        }

        @Override
        public E weakPeek() {
            final long currConsumerIndex = lvConsumerIndex();
            // other consumers may have grabbed the element, or queue might be empty
            final E e = lpElement(calcOffset(currConsumerIndex));
            return e;
        }
    }

    protected final Consumer<E> consumer;
    protected final Producer<E> producer;

    public MpmcArrayConcurrentQueueColdFields(int capacity) {
        super(capacity);
        consumer = new Consumer<E>(this);
        producer = new Producer<E>(this);
        producer.consumer = consumer;
        consumer.producer = producer;
    }
}

public final class MpmcArrayConcurrentQueue<E> extends MpmcArrayConcurrentQueueColdFields<E> implements
        ConcurrentQueue<E> {
    // post pad queue fields
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpmcArrayConcurrentQueue(final int capacity) {
        super(Math.max(2, capacity));
    }

    @Override
    public int size() {
        return (int) (((Producer<E>) producer).lvProducerIndex() - ((Consumer<E>) consumer).lvConsumerIndex());
    }

    @Override
    public int capacity() {
        return (int) this.mask + 1;
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
