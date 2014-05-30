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
package org.jctools.mpsc;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.ConcurrentQueue;
import org.jctools.ConcurrentQueueConsumer;
import org.jctools.ConcurrentQueueProducer;
import org.jctools.spsc.FFBufferWithOfferBatch;

/**
 * Use an SPSC per producer.
 */
abstract class MpscOnSpscL0Pad {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

@SuppressWarnings("unchecked")
abstract class MpscOnSpscFields<E> extends MpscOnSpscL0Pad {
    private final Queue<E>[] queues = new Queue[Integer.getInteger("producers", 4)];
    protected final ThreadLocal<Queue<E>> producerQueue;
    protected int tail;
    protected Queue<E> currentQ;

    public MpscOnSpscFields(final int capacity) {
        producerQueue = new ThreadLocal<Queue<E>>() {
            final AtomicInteger index = new AtomicInteger();

            @Override
            protected Queue<E> initialValue() {
                return queues[index.getAndIncrement() % queues.length];
            }
        };
        for (int i = 0; i < queues.length; i++) {
            queues[i] = new FFBufferWithOfferBatch<E>(capacity);
        }
        currentQ = queues[0];
    }

    protected final Queue<E>[] getQueues() {
        return queues;
    }
}

public final class MpscOnSpscQueue<E> extends MpscOnSpscFields<E> implements Queue<E>, ConcurrentQueue<E>,
        ConcurrentQueueConsumer<E>, ConcurrentQueueProducer<E> {
    long p40, p41, p42, p43, p44, p45, p46;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscOnSpscQueue(final int capacity) {
        super(capacity);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }

    public boolean offer(final E e) {
        return producerQueue.get().offer(e);
    }

    public E poll() {
        // int pCount = getProducerCount();
        // if(pCount == 0){
        // return null;
        // }
        Queue<E>[] qs = getQueues();
        // int start = ThreadLocalRandom.current().nextInt(pCount);
        // tail++;

        for (int i = 0; i < qs.length; i++) {
            E e = qs[i].poll();
            if (e != null)
                return e;
        }
        return null;
    }

    public E remove() {
        final E e = poll();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E element() {
        final E e = peek();
        if (null == e) {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E peek() {
        throw new UnsupportedOperationException();
    }

    public int size() {
        throw new UnsupportedOperationException();
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    public boolean contains(final Object o) {
        throw new UnsupportedOperationException();
    }

    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(final T[] a) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(final Collection<?> c) {
        for (final Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }

        return true;
    }

    public boolean addAll(final Collection<? extends E> c) {
        for (final E e : c) {
            add(e);
        }

        return true;
    }

    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        Object value;
        do {
            value = poll();
        } while (null != value);
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return this;
    }

    @Override
    public int capacity() {
        throw new UnsupportedOperationException();
    }
}
