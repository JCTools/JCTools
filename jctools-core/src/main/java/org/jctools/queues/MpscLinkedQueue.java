package org.jctools.queues;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class MpscLinkedQueuePad0<E> extends AbstractQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueProducerNodeRef<E> extends MpscLinkedQueuePad0<E> {
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<MpscLinkedQueueProducerNodeRef, LinkedQueueNode> UPDATER = 
            AtomicReferenceFieldUpdater.newUpdater(
                    MpscLinkedQueueProducerNodeRef.class, LinkedQueueNode.class, "producerNode");
    protected volatile LinkedQueueNode<E> producerNode = new LinkedQueueNode<>();

    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        return (LinkedQueueNode<E>) UPDATER.getAndSet(this, newVal);
    }
}

abstract class MpscLinkedQueuePad1<E> extends MpscLinkedQueueProducerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscLinkedQueueConsumerNodeRef<E> extends MpscLinkedQueuePad1<E> {
    protected LinkedQueueNode<E> consumerNode = producerNode;
}

public final class MpscLinkedQueue<E> extends MpscLinkedQueueConsumerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscLinkedQueue(){
        consumerNode = new LinkedQueueNode<E>();
        xchgProducerNode(consumerNode); // this ensures correct construction
    }
    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        LinkedQueueNode<E> n = new LinkedQueueNode<E>(e);
        LinkedQueueNode<E> prev = xchgProducerNode(n);
        prev.soNext(n);
        return true;
    }

    @Override
    public E poll() {
        LinkedQueueNode<E> n = consumerNode.lvNext();
        if (n != null) {
            consumerNode = n;
            return n.lpValue();
        }
        return null;
    }

    @Override
    public E peek() {
        return consumerNode.lvValue();
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        LinkedQueueNode<E> temp = consumerNode;
        int size = 0;
        while ((temp = temp.lvNext()) != null) {
            size++;
        }
        return size;
    }
}
