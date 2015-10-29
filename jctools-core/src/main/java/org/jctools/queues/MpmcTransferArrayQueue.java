/*
 * Copyright 2015 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues;

import static org.jctools.util.UnsafeRefArrayAccess.lpElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

import java.util.Random;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.locks.LockSupport;

import org.jctools.util.UnsafeAccess;

@SuppressWarnings("unused")
abstract class MpmcTransferQueueNodeColdItem<E> {
    private int type = 0;
    private E item = null;
}

abstract class MpmcTransferQueueNodePad<E> extends MpmcTransferQueueNodeColdItem<E> {
    volatile long y0, y1, y2, y4, y5, y6 = 7L;
}

@SuppressWarnings("unused")
abstract class MpmcTransferQueueNodeHotItem<E> extends MpmcTransferQueueNodePad<E> {
    private Thread thread;
}


/**
 * A bounded queue based on Nitsan's MpmcArrayQueue that offers
 * lower heap usage and better performance/scaling than
 * {@link LinkedTransferQueue}. The characteristics of this queue
 * are similar, yet subtly different than the LMAX disruptor. The
 * main distinction is that this queue will not fill the
 * ringbuffer when using the transfer/take methods. As a result,
 * there is very little flexibility for burst traffic that exceeds
 * the capabilities of the consumers. The expected behavior for
 * burst traffic is that the producers will slow down to match
 * the pace of the consumers.
 *
 * <p><p>
 * When using this queue as a transfer queue, it is sensitive to
 * the ratio of producers-to-consumers. In this mode, this queue
 * offers the best performance when the total time spent by the
 * consumers is operating at the same speed or faster than the
 * producers.
 *
 * <p><p>Unlike most queues, this implementation does not create
 * objects on the heap.
 *
 * <p><p>
 * Additionally, <b>DO NOT</b> mix transfer/take operations with
 * offer/poll, as they place incompatible data types on the queue.
 *
 * <p><p>
 * This queue orders elements FIFO (first-in-first-out) with respect
 * to any given producer.  In addition to queue.offer/poll, this
 * queue also supports transfer/take (from Java 7, TransferQueue interface),
 * which means that producers will block until a consumer is available
 * to receive the producer's object. Consumers that do not have data
 * available will block until a producer transfers data. This is the same
 * behavior as the {@link LinkedTransferQueue}.

 *
 * <p><p>The {@code size} method is a constant-time operation, however
 * because of the asynchronous nature of this queues, it is an
 * overestimation the actual size.
 *
 * <p><p>Memory consistency effects: As with other concurrent
 * collections, actions in a thread prior to placing an object into a
 * {@code MpmcTransferArrayQueue}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions subsequent to the access or removal of that element from
 * the {@code MpmcTransferArrayQueue} in another thread.
 *
 * <p><p> If it is possible for there to be more than 1024 threads running
 * concurrently, set the system property "MpmcTransferArrayQueue.size" to
 * be the expected max thread count. The default is tuned for consumer
 * hardware.
 *
 * @author dorkbox, llc
 * @param <E> the type of elements held in this collection
 */
public final class MpmcTransferArrayQueue<E> extends MpmcArrayQueue<E> {

    static class Node<E> extends MpmcTransferQueueNodeHotItem<E> {
        private static final long TYPE;
        private static final long ITEM;
        private static final long THREAD;
    
        static {
            try {
                TYPE = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcTransferQueueNodeColdItem.class.getDeclaredField("type"));
                ITEM = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcTransferQueueNodeColdItem.class.getDeclaredField("item"));
                THREAD = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcTransferQueueNodeHotItem.class.getDeclaredField("thread"));
    
                // now make sure we can access UNSAFE entries
                @SuppressWarnings("rawtypes")
                Node node = new Node();
                Object o = new Object();
                spItem(node, o);
                Object lpItem1 = lpItem(node);
                spItem(node, null);
    
                if (lpItem1 != o) {
                    throw new Exception("Cannot access unsafe fields");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    
        static final void spItem(Object node, Object item) {
            UnsafeAccess.UNSAFE.putObject(node, ITEM, item);
        }
    
        static final void soItem(Object node, Object item) {
            UnsafeAccess.UNSAFE.putOrderedObject(node, ITEM, item);
        }
    
        // only used by the single take() method. Not used by the void take(node)
        static final Object lvItem(Object node) {
            return UnsafeAccess.UNSAFE.getObjectVolatile(node, ITEM);
        }
    
        static final Object lpItem(Object node) {
            return UnsafeAccess.UNSAFE.getObject(node, ITEM);
        }
    
    
        //////////////
        static final void spType(Object node, int type) {
            UnsafeAccess.UNSAFE.putInt(node, TYPE, type);
        }
    
        static final int lpType(Object node) {
            return UnsafeAccess.UNSAFE.getInt(node, TYPE);
        }
    
        ///////////
        static final void spThread(Object node, Object thread) {
            UnsafeAccess.UNSAFE.putObject(node, THREAD, thread);
        }
    
        static final void soThread(Object node, Object thread) {
            UnsafeAccess.UNSAFE.putOrderedObject(node, THREAD, thread);
        }
    
        static final Object lpThread(Object node) {
            return UnsafeAccess.UNSAFE.getObject(node, THREAD);
        }
    
        static final Object lvThread(Object node) {
            return UnsafeAccess.UNSAFE.getObjectVolatile(node, THREAD);
        }
    
        // post-padding
        volatile long z0, z1, z2, z4, z5, z6 = 7L;
    
        public Node() {
        }
    }
    
    
    @SuppressWarnings("boxing")
    private static final int QUEUE_SIZE = Integer.getInteger("MpmcTransferArrayQueue.size", 1024);

    private static final int TYPE_EMPTY = 0;
    private static final int TYPE_CONSUMER = 1;
    private static final int TYPE_PRODUCER = 2;

    /** Is it multi-processor? */
    private static final boolean MP = Runtime.getRuntime().availableProcessors() > 1;

    private static final int INPROGRESS_SPINS = MP ? 32 : 0;
    private static final int PRODUCER_CAS_FAIL_SPINS = MP ? 512 : 0;
    private static final int CONSUMER_CAS_FAIL_SPINS = MP ? 512 : 0;


    /**
     * The number of times to spin before blocking in timed waits.
     * The value is empirically derived -- it works well across a
     * variety of processors and OSes. Empirically, the best value
     * seems not to vary with number of CPUs (beyond 2) so is just
     * a constant.
     */
    private static final int PARK_TIMED_SPINS = MP ? 32 : 0;

    /**
     * The number of times to spin before blocking in untimed waits.
     * This is greater than timed value because untimed waits spin
     * faster since they don't need to check times on each spin.
     */
    private static final int PARK_UNTIMED_SPINS = PARK_TIMED_SPINS * 16;

    private static final ThreadLocal<Object> nodeThreadLocal = new ThreadLocal<Object>() {
        @SuppressWarnings("rawtypes")
        @Override
        protected Object initialValue() {
            return new Node();
        }
    };

    private static final ThreadLocal<Random> randomThreadLocal = new ThreadLocal<Random>() {
        @Override
        protected
        Random initialValue() {
            return new Random();
        }
    };

    private final int consumerCount;


    /**
     * @param consumerCount is the defined number of consumers. This is necessary for {@link hasPendingMessages}.
     */
    public MpmcTransferArrayQueue(final int consumerCount) {
        super(QUEUE_SIZE); // must be power of 2. This is just an approximation, and systems with more than 1024 threads
        this.consumerCount = consumerCount;
    }



    /**
     * PRODUCER method
     * <p>
     * Place an item on the queue, and wait (if necessary) for a corresponding
     * consumer to take it. This will wait as long as necessary.
     */
    public void transfer(final E item) throws InterruptedException {
        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;
        final long[] sBuffer = this.sequenceBuffer;

        long consumerIndex;
        long producerIndex;
        int lastType;

        while (true) {
            consumerIndex = lvConsumerIndex();
            producerIndex = lvProducerIndex();

            if (consumerIndex == producerIndex) {
                lastType = TYPE_EMPTY;
            } else {
                final Object previousElement = lpElement(buffer, calcElementOffset(consumerIndex, mask));
                if (previousElement == null) {
                    // the last producer/consumer hasn't finished setting the object yet
                    busySpin(INPROGRESS_SPINS);
                    continue;
                }

                lastType = Node.lpType(previousElement);
            }

            if (lastType != TYPE_CONSUMER) {
                // TYPE_EMPTY, TYPE_PRODUCER
                // empty or same mode = push+park onto queue
                long pSeqOffset = calcSequenceOffset(producerIndex, mask);
                final long seq = lvSequence(sBuffer, pSeqOffset); // LoadLoad
                final long delta = seq - producerIndex;

                if (delta == 0) {
                    // this is expected if we see this first time around
                    final long newProducerIndex = producerIndex + 1;
                    if (casProducerIndex(producerIndex, newProducerIndex)) {
                        // Successful CAS: full barrier

                        final Thread myThread = Thread.currentThread();
                        final Object node = nodeThreadLocal.get();

                        Node.spType(node, TYPE_PRODUCER);
                        Node.spThread(node, myThread);
                        Node.spItem(node, item);


                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(producerIndex, mask);
                        spElement(buffer, offset, node);


                        // increment sequence by 1, the value expected by consumer
                        // (seeing this value from a producer will lead to retry 2)
                        soSequence(sBuffer, pSeqOffset, newProducerIndex); // StoreStore

                        park(node, myThread);

                        return;
                    } else {
                        busySpin(PRODUCER_CAS_FAIL_SPINS);
                    }
                }
            }
            else {
                // TYPE_CONSUMER
                // complimentary mode = pop+unpark off queue
                long cSeqOffset = calcSequenceOffset(consumerIndex, mask);
                final long seq = lvSequence(sBuffer, cSeqOffset); // LoadLoad
                final long newConsumerIndex = consumerIndex + 1;
                final long delta = seq - newConsumerIndex;

                if (delta == 0) {
                    if (casConsumerIndex(consumerIndex, newConsumerIndex)) {
                        // Successful CAS: full barrier

                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(consumerIndex, mask);
                        final Object e = lpElement(buffer, offset);
                        spElement(buffer, offset, null);

                        // Move sequence ahead by capacity, preparing it for next offer
                        // (seeing this value from a consumer will lead to retry 2)
                        soSequence(sBuffer, cSeqOffset, mask + newConsumerIndex); // StoreStore

                        Node.spItem(e, item);
                        unpark(e);  // StoreStore

                        return;
                    } else {
                        busySpin(CONSUMER_CAS_FAIL_SPINS);
                    }
                }
            }
        }
    }

    /**
     * CONSUMER
     * <p>
     * Remove an item from the queue. If there are no items on the queue, wait
     * for a producer to place an item on the queue. This will wait as long
     * as necessary.
     */
    public E take() throws InterruptedException {
        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;
        final long[] sBuffer = this.sequenceBuffer;

        long consumerIndex;
        long producerIndex;
        int lastType;

        while (true) {
            consumerIndex = lvConsumerIndex();
            producerIndex = lvProducerIndex();

            if (consumerIndex == producerIndex) {
                lastType = TYPE_EMPTY;
            } else {
                final Object previousElement = lpElement(buffer, calcElementOffset(consumerIndex, mask));
                if (previousElement == null) {
                    // the last producer/consumer hasn't finished setting the object yet
                    busySpin(INPROGRESS_SPINS);
                    continue;
                }

                lastType = Node.lpType(previousElement);
            }

            if (lastType != TYPE_PRODUCER) {
                // TYPE_EMPTY, TYPE_CONSUMER
                // empty or same mode = push+park onto queue
                long pSeqOffset = calcSequenceOffset(producerIndex, mask);
                final long seq = lvSequence(sBuffer, pSeqOffset); // LoadLoad
                final long delta = seq - producerIndex;

                if (delta == 0) {
                    // this is expected if we see this first time around
                    final long newProducerIndex = producerIndex + 1;
                    if (casProducerIndex(producerIndex, newProducerIndex)) {
                        // Successful CAS: full barrier

                        final Thread myThread = Thread.currentThread();
                        final Object node = nodeThreadLocal.get();

                        Node.spType(node, TYPE_CONSUMER);
                        Node.spThread(node, myThread);

                        // the unpark thread sets our contents

                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(producerIndex, mask);
                        spElement(buffer, offset, node);


                        // increment sequence by 1, the value expected by consumer
                        // (seeing this value from a producer will lead to retry 2)
                        soSequence(sBuffer, pSeqOffset, newProducerIndex); // StoreStore

                        park(node, myThread);

                        @SuppressWarnings("unchecked")
                        E item = (E) Node.lvItem(node);
                        return item;
                    } else {
                        busySpin(PRODUCER_CAS_FAIL_SPINS);
                    }
                }
            }
            else {
                // TYPE_PRODUCER
                // complimentary mode = pop+unpark off queue
                long cSeqOffset = calcSequenceOffset(consumerIndex, mask);
                final long seq = lvSequence(sBuffer, cSeqOffset); // LoadLoad
                final long newConsumerIndex = consumerIndex + 1;
                final long delta = seq - newConsumerIndex;

                if (delta == 0) {
                    if (casConsumerIndex(consumerIndex, newConsumerIndex)) {
                        // Successful CAS: full barrier

                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(consumerIndex, mask);
                        final Object e = lpElement(buffer, offset);
                        spElement(buffer, offset, null);

                        // Move sequence ahead by capacity, preparing it for next offer
                        // (seeing this value from a consumer will lead to retry 2)
                        soSequence(sBuffer, cSeqOffset, mask + newConsumerIndex); // StoreStore

                        @SuppressWarnings("unchecked")
                        final E lvItem = (E) Node.lpItem(e);
                        unpark(e); // StoreStore

                        return lvItem;
                    } else {
                        busySpin(CONSUMER_CAS_FAIL_SPINS);
                    }
                }
            }
        }
    }

    /**
     * CONSUMER
     * <p>
     * Remove an item from the queue. If there are no items on the queue, wait
     * for a producer to place an item on the queue. This will wait as long as
     * necessary.
     * <p>
     * This method does not depend on thread-local for node information, and
     * so is more efficient, and the node used by this method will contain the
     * data.
     */
    @SuppressWarnings("unchecked")
    public void take(Node<E> node) throws InterruptedException {
        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;
        final long[] sBuffer = this.sequenceBuffer;

        long consumerIndex;
        long producerIndex;
        int lastType;

        while (true) {
            consumerIndex = lvConsumerIndex();
            producerIndex = lvProducerIndex();

            if (consumerIndex == producerIndex) {
                lastType = TYPE_EMPTY;
            } else {
                final Object previousElement = lpElement(buffer, calcElementOffset(consumerIndex, mask));
                if (previousElement == null) {
                    // the last producer/consumer hasn't finished setting the object yet
                    busySpin(INPROGRESS_SPINS);
                    continue;
                }

                lastType = Node.lpType(previousElement);
            }

            if (lastType != TYPE_PRODUCER) {
                // TYPE_EMPTY, TYPE_CONSUMER
                // empty or same mode = push+park onto queue
                long pSeqOffset = calcSequenceOffset(producerIndex, mask);
                final long seq = lvSequence(sBuffer, pSeqOffset); // LoadLoad
                final long delta = seq - producerIndex;

                if (delta == 0) {
                    // this is expected if we see this first time around
                    final long newProducerIndex = producerIndex + 1;
                    if (casProducerIndex(producerIndex, newProducerIndex)) {
                        // Successful CAS: full barrier

                        final Thread myThread = Thread.currentThread();
                        // final Object node = nodeThreadLocal.get();

                        Node.spType(node, TYPE_CONSUMER);
                        Node.spThread(node, myThread);

                        // the unpark thread sets our contents

                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(producerIndex, mask);
                        spElement(buffer, offset, node);


                        // increment sequence by 1, the value expected by consumer
                        // (seeing this value from a producer will lead to retry 2)
                        soSequence(sBuffer, pSeqOffset, newProducerIndex); // StoreStore

                        park(node, myThread);

                        return;
                        // @SuppressWarnings("unchecked")
                        // E item = (E) Node.lvItem(node);
                        // return item;
                    } else {
                        busySpin(PRODUCER_CAS_FAIL_SPINS);
                    }
                }
            }
            else {
                // TYPE_PRODUCER
                // complimentary mode = pop+unpark off queue
                long cSeqOffset = calcSequenceOffset(consumerIndex, mask);
                final long seq = lvSequence(sBuffer, cSeqOffset); // LoadLoad
                final long newConsumerIndex = consumerIndex + 1;
                final long delta = seq - newConsumerIndex;

                if (delta == 0) {
                    if (casConsumerIndex(consumerIndex, newConsumerIndex)) {
                        // Successful CAS: full barrier

                        // on 64bit(no compressed oops) JVM this is the same as seqOffset
                        final long offset = calcElementOffset(consumerIndex, mask);
                        final Object e = lpElement(buffer, offset);
                        spElement(buffer, offset, null);

                        // Move sequence ahead by capacity, preparing it for next offer
                        // (seeing this value from a consumer will lead to retry 2)
                        soSequence(sBuffer, cSeqOffset, mask + newConsumerIndex); // StoreStore

                        Node.spItem(node, Node.lpItem(e));
                        unpark(e); // StoreStore

                        return;
                    } else {
                        busySpin(CONSUMER_CAS_FAIL_SPINS);
                    }
                }
            }
        }
    }

    public boolean hasPendingMessages() {
        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;

        long consumerIndex;
        long producerIndex;

        while (true) {
            consumerIndex = lvConsumerIndex();
            producerIndex = lvProducerIndex();

            if (consumerIndex == producerIndex) {
                return true;
            } else {
                final Object previousElement = lpElement(buffer, calcElementOffset(consumerIndex, mask));
                if (previousElement == null) {
                    // the last producer/consumer hasn't finished setting the object yet
                    busySpin(INPROGRESS_SPINS);
                    continue;
                }

                return Node.lpType(previousElement) != TYPE_CONSUMER || consumerIndex + this.consumerCount != producerIndex;
            }
        }
    }

    private static final void busySpin(int spins) {
        for (;;) {
            if (spins > 0) {
                --spins;
            } else {
                return;
            }
        }
    }

    @SuppressWarnings("null")
    private void park(final Object node, final Thread myThread) throws InterruptedException {
        int spins = -1; // initialized after first item and cancel checks
        Random randomYields = null; // bound if needed

        for (;;) {
            if (Node.lvThread(node) == null) {
                return;
            } else if (myThread.isInterrupted()) {
                throw new InterruptedException();
            } else if (spins < 0) {
                spins = PARK_UNTIMED_SPINS;
                randomYields = randomThreadLocal.get();
            } else if (spins > 0) {
                if (randomYields.nextInt(1024) == 0) {
                    LockSupport.park();
                }
                --spins;
            } else {
                // park can return for NO REASON (must check for thread values)
                LockSupport.park();
            }
        }
    }

    private void unpark(Object node) {
        final Object thread = Node.lpThread(node);
        Node.soThread(node, null);  // StoreStore
        UnsafeAccess.UNSAFE.unpark(thread); // using unsafe to avoid casting
    }
}
