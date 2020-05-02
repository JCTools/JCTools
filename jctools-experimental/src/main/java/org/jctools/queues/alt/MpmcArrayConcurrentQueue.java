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
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class MpmcArrayConcurrentQueueColdFields<E> extends ConcurrentSequencedCircularArray<E> {

    private static abstract class ProducerFields<E> extends ConcurrentSequencedCircularArray<E> {
        protected static final long P_INDEX_OFFSET = fieldOffset(ProducerFields.class, "producerIndex");

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
                        currIndex - capacity <= (cIndex = consumer.lvConsumerIndex())) { // test against
                                                                                         // latest
                    // Extra check required to ensure [Queue.offer == false iff queue is full]
                    return false;
                }
                // another producer has moved the sequence by one, retry 2
            }
            addElement(sBuffer, buffer, mask, currIndex, sOffset, e);

            return true;
        }

        private void addElement(long[] sBuffer, E[] eBuffer, long mask, long index, long sOffset, final E e) {
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
            final long[] sBuffer = sequenceBuffer;
            final long mask = this.mask;
            final E[] eBuffer = buffer;
            return weakOffer(sBuffer, eBuffer, mask, e);
        }

        private boolean weakOffer(final long[] sBuffer, final E[] eBuffer, final long mask, E e) {
            long currIndex;
            long sOffset;

            while(true) {
                currIndex = lvProducerIndex();
                sOffset = calcSequenceOffset(currIndex, mask);
                long delta = lvSequence(sBuffer, sOffset) - currIndex;
                if (delta == 0) {
                    // this is expected if we see this first time around
                    if (casProducerIndex(currIndex, currIndex + 1)) {
                        break;
                    }
                    // failed cas, retry 1
                } else if (delta < 0) {
                    // poll has not moved this value forward, but that doesn't mean
                    // queue is full.
                    return false;
                }
            }

            addElement(sBuffer, eBuffer, mask, currIndex, sOffset, e);
            return true;
        }

        @Override
        public int produce(ProducerFunction<E> producer, int batchSize) {
            final long[] sBuffer = this.sequenceBuffer;
            final E[] eBuffer = this.buffer;
            final long mask = this.mask;
            long tDelta;
            long currIndex;
            long targetIndex;
            do {
                currIndex = lvProducerIndex();// LoadLoad
                targetIndex = currIndex + batchSize - 1;
                final long tsOffset = calcSequenceOffset(targetIndex, mask);
                tDelta = lvSequence(sBuffer, tsOffset) - targetIndex;
            } while ((tDelta == 0 && !casProducerIndex(currIndex, targetIndex+1)) || tDelta > 0);

            if (tDelta == 0) {
                // can now produce all elements
                for (int i = 0; i < batchSize; i++) {
                    // The 'availability' of the target slot is not indicative of the 'availability' of every slot up to
                    // it. We have to consume all the elements now that we claimed the slots, so if they are not visible
                    // we must block.
                    long eSequence;
                    long eDelta;
                    long sOffset;
                    do {
                        sOffset = calcSequenceOffset(currIndex, mask);
                        eSequence = lvSequence(sBuffer, sOffset);// LoadLoad
                        eDelta = eSequence - currIndex;
                    } while (eDelta != 0);
                    addElement(sBuffer, eBuffer, mask, currIndex++, sOffset, producer.produce());
                }
                return batchSize;
            }
            // bugger, queue is either:
            // (a) full
            // (b) has less than batchSize slots open
            // (c) slot at batch target is not available
            // we take the easy way out and weakOffer through this case
            int i = 0;
            for (; i < batchSize; i++) {
                if (!weakOffer(sBuffer, eBuffer, mask, producer.produce())) {
                    break;
                }
            }
            return i;
        }
    }

    private static abstract class ConsumerFields<E> extends ConcurrentSequencedCircularArray<E> {
        protected static final long C_INDEX_OFFSET = fieldOffset(ConsumerFields.class, "consumerIndex");

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

        private Consumer(ConcurrentSequencedCircularArray<E> c) {
            super(c);
        }

        @Override
        public E weakPoll() {
            // local load of field to avoid repeated loads after volatile reads
            return weakPoll(sequenceBuffer, buffer, mask);
        }

        private E weakPoll(final long[] sBuffer, E[] eBuffer, final long mask) {
            long currIndex;
            long sOffset;
            while (true) {
                currIndex = lvConsumerIndex();// LoadLoad
                sOffset = calcSequenceOffset(currIndex, mask);
                final long delta = lvSequence(sBuffer, sOffset) - (currIndex + 1);

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

            return consumeElement(sBuffer, eBuffer, mask, currIndex, sOffset);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Because return null indicates queue is empty we cannot simply rely on next element visibility for poll and
         * must test producer index when next element is not visible.
         */
        @Override
        public E poll() {
            // local load of fields to avoid repeated loads after volatile reads
            final long[] sBuffer = sequenceBuffer;
            final long mask = this.mask;
            final Producer<E> producer = this.producer;
            long currentConsumerIndex;
            long sOffset;
            long pIndex = -1; // start with bogus value, hope we don't need it
            while (true) {
                currentConsumerIndex = lvConsumerIndex();// LoadLoad
                sOffset = calcSequenceOffset(currentConsumerIndex, mask);
                final long delta = lvSequence(sBuffer, sOffset) - (currentConsumerIndex + 1);

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

            return consumeElement(sBuffer, buffer, mask, currentConsumerIndex, sOffset);
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

        private E consumeElement(long[] sBuffer, E[] eBuffer, long mask, long currIndex, long sOffset) {
            final long eOffset = calcOffset(currIndex, mask);
            final E e = lpElement(eOffset);
            spElement(eBuffer, eOffset, null);

            // Move sequence ahead by capacity, preparing it for next offer
            // (seeing this value from a consumer will lead to retry 2)
            soSequence(sBuffer, sOffset, currIndex + mask + 1);// StoreStore
            return e;
        }

        @Override
        public int consume(ConsumerFunction<E> consumer, int batchSize) {
            final long[] sBuffer = this.sequenceBuffer;
            final E[] eBuffer = this.buffer;
            final long mask = this.mask;
            long tDelta;
            long currIndex;
            long targetIndex;
            do {
                currIndex = lvConsumerIndex();// LoadLoad
                targetIndex = currIndex + batchSize - 1;
                final long sOffset = calcSequenceOffset(targetIndex, mask);
                tDelta = lvSequence(sBuffer, sOffset) - (targetIndex + 1);
            } while ((tDelta == 0 && !casConsumerIndex(currIndex, targetIndex+1)) || tDelta > 0);

            if (tDelta == 0) {
                // can now consume all elements
                for (int i = 0; i < batchSize; i++) {
                    // The 'availability' of the target slot is not indicative of the 'availability' of every slot up to
                    // it. We have to consume all the elements now that we claimed the slots, so if they are not visible
                    // we must block.
                    long eDelta;
                    long sOffset;
                    do {
                        sOffset = calcSequenceOffset(currIndex, mask);
                        eDelta = lvSequence(sBuffer, sOffset) - (currIndex + 1);
                    } while (eDelta != 0);
                    E e = consumeElement(sBuffer, eBuffer, mask, currIndex++, sOffset);
                    consumer.consume(e);
                }
                return batchSize;
            }
            // bugger, queue is either:
            // (a) empty
            // (b) has less than batchSize elements
            // (c) slot at batch target is unavailable
            // we take the easy way out and weakPoll through this case
            int i = 0;
            for (; i < batchSize; i++) {
                final E e = weakPoll(sBuffer, eBuffer, mask);
                if (e == null) {
                    break;
                }
                consumer.consume(e);
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

    public MpmcArrayConcurrentQueue(final int capacity) {
        super(Math.max(2, capacity));
    }

    @Override
    public int size() {
        return (int) (producer.lvProducerIndex() - consumer.lvConsumerIndex());
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
