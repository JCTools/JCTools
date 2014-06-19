package org.jctools.queues;

import java.util.AbstractQueue;
import java.util.Iterator;

abstract class SpscLinkedQueuePad0<E> extends AbstractQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpscLinkedQueueProducerNodeRef<E> extends SpscLinkedQueuePad0<E> {
    protected LinkedQueueNode<E> producerNode = new LinkedQueueNode<>();;
}

abstract class SpscLinkedQueuePad1<E> extends SpscLinkedQueueProducerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class SpscLinkedQueueConsumerNodeRef<E> extends SpscLinkedQueuePad1<E> {
    protected LinkedQueueNode<E> consumerNode = producerNode;
}

public final class SpscLinkedQueue<E> extends SpscLinkedQueueConsumerNodeRef<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public SpscLinkedQueue() {
        consumerNode.soNext(null); // this ensures correct construction: StoreStore
    }
    @Override
    public boolean offer(E e) {
        if(e == null) {
            throw new IllegalArgumentException("null elements not allowed");
        }
        LinkedQueueNode<E> n = new LinkedQueueNode<E>(e);
        producerNode.soNext(n);
        producerNode = n;
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
        while ((temp = temp.lvNext()) != null){
            size++;
        }
        return size;
    }
}
