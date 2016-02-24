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

import org.jctools.util.UnsafeAccess;

import java.util.Random;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.locks.LockSupport;

import static org.jctools.util.UnsafeRefArrayAccess.lpElement;
import static org.jctools.util.UnsafeRefArrayAccess.soElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

@SuppressWarnings("unused")
abstract class MpmcTransferQueueNodeColdItem<E> {
    public E item = null;
}

abstract class MpmcTransferQueueNodePad<E> extends MpmcTransferQueueNodeColdItem<E> {
    volatile long y0, y1, y2, y3, y4, y5, y6 = 7L;
}

@SuppressWarnings("unused")
abstract class MpmcTransferQueueNodeHotItem<E> extends MpmcTransferQueueNodePad<E> {
    public Thread thread;
}

class MpmcTransferQueueNode<E> extends MpmcTransferQueueNodeHotItem<E> {
    private static final long ITEM;
    private static final long THREAD;

    static {
        try {
            ITEM = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcTransferQueueNodePad.class.getDeclaredField("item"));
            THREAD = UnsafeAccess.UNSAFE.objectFieldOffset(MpmcTransferQueueNode.class.getDeclaredField("thread"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

//    static final void spItem(Object node, Object item) {
//        UnsafeAccess.UNSAFE.putObject(node, ITEM, item);
//    }
//
//    static final void soItem(Object node, Object item) {
//        UnsafeAccess.UNSAFE.putOrderedObject(node, ITEM, item);
//    }
//
//    static final Object lvItem(Object node) {
//        return UnsafeAccess.UNSAFE.getObjectVolatile(node, ITEM);
//    }

    static final Object lpItem(Object node) {
        return UnsafeAccess.UNSAFE.getObject(node, ITEM);
    }

//    static final void spThread(Object node, Object thread) {
//        UnsafeAccess.UNSAFE.putObject(node, THREAD, thread);
//    }

    static final void soThread(Object node, Object thread) {
        UnsafeAccess.UNSAFE.putOrderedObject(node, THREAD, thread);
    }

    static final Object lpThread(Object node) {
        return UnsafeAccess.UNSAFE.getObject(node, THREAD);
    }

    static final Object lvThread(Object node) {
        return UnsafeAccess.UNSAFE.getObjectVolatile(node, THREAD);
    }

//    static final boolean casThread(Object node, Object threadCurrent, Object threadNew) {
//        return UnsafeAccess.UNSAFE.compareAndSwapObject(node, THREAD, threadCurrent, threadNew);
//    }
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
 * @param <T> the type of elements held in this collection
 */
@SuppressWarnings("Duplicates")
public class MpmcArrayTransferQueue<T> extends MpmcArrayQueue<T> {
    @SuppressWarnings("boxing")
    private static final int QUEUE_SIZE = Integer.getInteger("MpmcTransferArrayQueue.size", 1024);

    /**
     * The number of times to spin (with randomly interspersed calls
     * to Thread.yield) on multiprocessor before blocking when a node
     * is apparently the first waiter in the queue.  See above for
     * explanation. Must be a power of two. The value is empirically
     * derived -- it works pretty well across a variety of processors,
     * numbers of CPUs, and OSes.
     */
    private static final int FRONT_SPINS   = 1 << 7;

    private static final ThreadLocal<Random> randomThreadLocal = new ThreadLocal<Random>() {
        @Override
        protected
        Random initialValue() {
            return new Random();
        }
    };

    private final ThreadLocal<MpmcTransferQueueNode<T>> nodeThreadLocal = new ThreadLocal<MpmcTransferQueueNode<T>>() {
        @SuppressWarnings("rawtypes")
        @Override
        protected MpmcTransferQueueNode<T> initialValue() {
            return new MpmcTransferQueueNode<T>();
        }
    };

    public
    MpmcArrayTransferQueue() {
        super(QUEUE_SIZE); // must be power of 2. This is just an approximation, and systems with more than 1024 threads

        // Prevent rare disastrous classloading in first call to LockSupport.park.
        // See: https://bugs.openjdk.java.net/browse/JDK-8074773
        try {
            Class.forName(LockSupport.class.getName(), true, LockSupport.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new Error(e);
        }
    }

    /**
     * PRODUCER method
     * <p>
     * Place an item on the queue, and wait (as long as needed, if necessary) for a corresponding
     * consumer to take it.
     */
    public void transfer(final T item) throws InterruptedException {
        final MpmcTransferQueueNode<T> node = nodeThreadLocal.get();

        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;
        final long[] seqBuffer = this.sequenceBuffer;

        long cIndex;
        long pIndex;

        // ASSUMPTION: if queue is not empty it must be either full of Transfer or Take nodes
        while (true) {
            cIndex = lvConsumerIndex();
            pIndex = lvProducerIndex();

            if (cIndex == pIndex) {
                // EMPTY -> push a transfer node and park
                if (pushAndPark(node, item, mask, buffer, seqBuffer, pIndex)) {
                    return;
                }
                // busySpin(CHAINED_SPINS);
            }
            else {
                // NON-EMPTY -> check the first node (from the consumer index) to see what type it is.

                // CRITICAL WARNING: the node item in this element can potentially be changed while in use.
                final long nodeOffset = calcElementOffset(cIndex, mask);
                final Object firstNode1 = lpElement(buffer, nodeOffset);

                if (firstNode1 != null) {
                    // another producer/consumer hasn't finished setting the object yet

                    MpmcTransferQueueNode<T> firstNode = (MpmcTransferQueueNode<T>)firstNode1;
                    T firstItem = firstNode.item; // this requires ordering the element and type stores

                    if (firstItem != null) {
                        // TRANSFER -> same mode -> push a transfer node and park
                        if (pushAndPark(node, item, mask, buffer, seqBuffer, pIndex)) {
                            return;
                        }
                    }
                    else {
                        // TAKE -> immediate transfer -> pop a take node, hand item + unpark taker
                        if (takeAndUnpark(item, mask, buffer, seqBuffer, cIndex, nodeOffset, firstNode)) {
                            return; // CAS FAILED: Retry
                        }
                    }
                }
                // busySpin(FRONT_SPINS);
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
    public
    T take() throws InterruptedException {
        final MpmcTransferQueueNode<T> node = nodeThreadLocal.get();
        return take(node);
    }

    /**
     * CONSUMER
     * <p>
     * Remove an item from the queue. If there are no items on the queue, wait
     * for a producer to place an item on the queue. This will wait as long as
     * necessary.
     * <p>
     * This method does not depend on thread-local for node information, and
     * so *can be* more efficient. The node used by this method will contain the
     * data.
     */
    @SuppressWarnings("unchecked")
    public T take(MpmcTransferQueueNode<T> node) throws InterruptedException {
        // local load of field to avoid repeated loads after volatile reads
        final long mask = this.mask;
        final Object[] buffer = this.buffer;
        final long[] seqBuffer = this.sequenceBuffer;

        long cIndex;
        long pIndex;

        // ASSUMPTION: if queue is not empty it must be either full of Transfer or Take nodes
        while (true) {
            cIndex = lvConsumerIndex();
            pIndex = lvProducerIndex();

            if (cIndex == pIndex) {
                // EMPTY -> push a take node and park
                if (pushAndPark(node, null, mask, buffer, seqBuffer, pIndex)) {
                    return node.item;
                }
                // busySpin(CHAINED_SPINS);
            }
            else {
                // NON-EMPTY -> check the first node (from the consumer index) to see what type it is.

                // CRITICAL WARNING: the node item in this element can potentially be changed while in use.
                final long nodeOffset = calcElementOffset(cIndex, mask); // on 64bit(no compressed oops) JVM this is the same as seqOffset
                final Object firstNode1 = lpElement(buffer, nodeOffset);

                if (firstNode1 != null) {
                    // another producer/consumer hasn't finished setting the object yet

                    MpmcTransferQueueNode<T> firstNode = (MpmcTransferQueueNode<T>)firstNode1;
                    T firstItem = firstNode.item; // this requires ordering the element and type stores

                    if (firstItem == null) {
                        // TAKE -> same mode -> push a take node and park
                        if (pushAndPark(node, null, mask, buffer, seqBuffer, pIndex)) {
                            // now have valid node, return node item
                            return node.item;
                        }
                    }
                    else {
                        // TRANSFER -> immediate take -> pop a transfer node, take item + unpark transfer-er
                        if (takeAndUnpark(null, mask, buffer, seqBuffer, cIndex, nodeOffset, firstNode)) {
                            return firstItem;
                        }
                    }
                }

                // busySpin(FRONT_SPINS);
            }
        }
    }

    /**
     * @return true if successfully pushed an item to the queue
     */
    private
    boolean pushAndPark(final MpmcTransferQueueNode<T> node, final T item, final long mask, final Object[] buffer, final
    long[] seqBuffer, final long pIndex)
                    throws InterruptedException {

        final long pIndexNext = pIndex + 1;
        final long pSeqOffset = calcSequenceOffset(pIndex, mask);
        long pSequence = lvSequence(seqBuffer, pSeqOffset);


        if (pSequence != pIndex || // another producer has moved the sequence  OR  consumer has not moved this value forward
            !casProducerIndex(pIndex, pIndexNext)) { // failed to increment
            return false; // CAS FAILED: Retry
        }

        // SUCCESS
        final Thread myThread = Thread.currentThread();

        // save the node (type/item/thread) at this position and park
        node.item = item;
        node.thread = myThread;

        // on 64bit(no compressed oops) JVM this is the same as seqOffset
        final long nodeOffset = calcElementOffset(pIndex, mask);
        soElement(buffer, nodeOffset, node); // required so type is correctly published

        // increment sequence by 1, the value expected by consumer
        // (seeing this value from a producer will lead to retry)
        soSequence(sequenceBuffer, pSeqOffset, pIndexNext); // StoreStore
        parkAndSpinNode(node, myThread);

        return true;
    }

    /**
     * Take an item from the queue and unpark it
     */
    private
    boolean takeAndUnpark(final T item,
                          final long mask,
                          final Object[] buffer,
                          final long[] seqBuffer,
                          final long cIndex,
                          final long nodeOffset,
                          final MpmcTransferQueueNode<T> node) {

        final long cIndexNext = cIndex + 1;
        final long cSeqOffset = calcSequenceOffset(cIndex, mask);
        final long cSequence = lvSequence(seqBuffer, cSeqOffset);

        if (cSequence != cIndexNext || // another consumer beat us to it  OR  slot has not been moved by producer
            !casConsumerIndex(cIndex, cIndexNext)) { // failed the CAS
            return false;  // CAS FAILED: Retry
        }

        // SUCCESS

        // set the item to the node (or clear if item == null)
        node.item = item;

        // advance the sequence
        spElement(buffer, nodeOffset, null); // clear the old sequence value, it is eventually published

        // Move sequence ahead by capacity, preparing it for next offer
        // (seeing this value from a consumer will lead to retry)
        soSequence(seqBuffer, cSeqOffset, cIndexNext + mask); // StoreStore


        // unpark
        final Object thread = MpmcTransferQueueNode.lpThread(node);
        MpmcTransferQueueNode.soThread(node, null);  // signals genuine unpark
        UnsafeAccess.UNSAFE.unpark(thread); // using unsafe to avoid casting

        return true;
    }

    private static void busySpin(int spins) {
        for (;;) {
            if (spins > 0) {
                --spins;
            } else {
                return;
            }
        }
    }

    /**
     * @return true if there is no data waiting on the queue to be taken, false if there is data waiting to be taken
     */
    public
    boolean isEmpty() {
        final long mask = this.mask;
        final Object[] buffer = this.buffer;

        long consumerIndex;
        long producerIndex;

        while (true) {
            consumerIndex = lvConsumerIndex();
            producerIndex = lvProducerIndex();

            if (consumerIndex == producerIndex) {
                return true; // EMPTY
            }
            else {
                final Object firstNode = lpElement(buffer, calcElementOffset(consumerIndex, mask));
                if (firstNode == null) {
                    continue; // the last producer/consumer hasn't finished setting the object yet
                }

                // queue is either full of transfers or full of waiting takers
                final Object item = MpmcTransferQueueNode.lpItem(firstNode);
                if (consumerIndex != lvConsumerIndex()) {
                    continue; // consumer index moved while we examine node type, try again
                }
                return item == null;
            }
        }
    }

    private
    void parkAndSpinNode(final MpmcTransferQueueNode<T> node, final Thread myThread) throws InterruptedException {
        int spins = -1; // initialized after first item and cancel checks
        Random randomYields = null; // bound if needed

        for (; ; ) {
            if (MpmcTransferQueueNode.lvThread(node) == null) {
                return;
            }
            else if (myThread.isInterrupted()) {
                throw new InterruptedException();
            }
            else if (spins < 0) {
                spins = FRONT_SPINS;
                randomYields = randomThreadLocal.get();
            }
            else if (spins > 0) {
                if (randomYields.nextInt(1024) == 0) {
                    Thread.yield();
                }
                --spins;
            }
            else {
                // park can return for NO REASON (must check for thread values)
                LockSupport.park(this);
            }
        }
    }
}
