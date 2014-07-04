package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class MpscLinkedQueue7Pad0<E> extends AbstractQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueue7ProducerNodeRef<E> extends MpscLinkedQueue7Pad0<E> {
    protected final static long P_NODE_OFFSET;

    static {
        try {
            P_NODE_OFFSET = UNSAFE.objectFieldOffset(MpscLinkedQueue7ProducerNodeRef.class.getDeclaredField("producerNode"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
    protected volatile LinkedQueueNode<E> producerNode;

    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        Object oldVal;
        do {
            oldVal = producerNode;
        } while(!UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, oldVal, newVal));
        return (LinkedQueueNode<E>) oldVal;
    }
}

abstract class MpscLinkedQueue7Pad1<E> extends MpscLinkedQueue7ProducerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueue7ConsumerNodeRef<E> extends MpscLinkedQueue7Pad1<E> {
    protected LinkedQueueNode<E> consumerNode;
}

/**
 * This is a direct Java port of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on 1024
 * Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory model and
 * layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>Use {@link AtomicReferenceFieldUpdater} to provide XCHG functionality to the best of the JDK ability.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references. From this
 * point follow the notes on offer/poll.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class MpscLinkedQueue7<E> extends MpscLinkedQueue7ConsumerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscLinkedQueue7() {
        consumerNode = new LinkedQueueNode<>();
        producerNode = consumerNode;// this ensures correct construction: StoreLoad
    }

    /**
     * {@inheritDoc} <br>
     * 
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2 producers can
     * get the same producer node as part of XCHG guarantee.
     * 
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(final E nextValue) {
        if (nextValue == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<E>(nextValue);
        final LinkedQueueNode<E> prevProducerNode = xchgProducerNode(nextNode);
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next.
        prevProducerNode.soNext(nextNode); // StoreStore
        return true;
    }

    /**
     * {@inheritDoc} <br>
     * 
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is empty.
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue. Because null
     * values are not allowed to be offered this is the only node with it's value set to null at any one time.
     * 
     * @see MessagePassingQueue#poll(Object)
     * @see java.util.Queue#poll(java.lang.Object)
     */
    @Override
    public E poll() {
        E e;
        // if the queue is truly empty these 2 are the same. Sadly this means we spin on the producer field...
        while ((e = tryPoll()) == null && producerNode != consumerNode) {
            // spin
        }
        return e;
    }

    public E tryPoll() {
        LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.evacuateValue();
            consumerNode = nextNode;
            return nextValue;
        }
        return null;
    }
    
    @Override
    public E peek() {
        E e;
        // if the queue is truly empty these 2 are the same. Sadly this means we spin on the producer field...
        while ((e = tryPeek()) == null && producerNode != consumerNode) {
            // spin
        }
        return e;
    }
    
    public E tryPeek() {
        LinkedQueueNode<E> nextNode = consumerNode.lvNext();
        if (nextNode != null) {
            return nextNode.lpValue();
        }
        return null;
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc} <br>
     * 
     * IMPLEMENTATION NOTES:<br>
     * This is an O(n) operation as we run through all the nodes and count them.<br>
     * 
     * @see java.util.Queue#size()
     */
    @Override
    public int size() {
        LinkedQueueNode<E> temp = consumerNode;
        int size = 0;
        while ((temp = temp.lvNext()) != null && size < Integer.MAX_VALUE) {
            size++;
        }
        return size;
    }
}
