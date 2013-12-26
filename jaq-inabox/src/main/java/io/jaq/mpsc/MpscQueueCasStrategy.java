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

import io.jaq.util.UnsafeAccess;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

public final class MpscQueueCasStrategy<E> extends MpscConcurrentArrayQueueL3Pad<E> implements Queue<E> {
	private static final BackOffStrategy CAS_BACKOFF = BackOffStrategy.getStrategy("cas.backoff", BackOffStrategy.SPIN);
    public MpscQueueCasStrategy(final int capacity) {
        super(capacity);
    }

    private long getHead() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, HEAD_OFFSET);
    }
    private void lazySetHead(long l) {
        UnsafeAccess.UNSAFE.putOrderedLong(this, HEAD_OFFSET, l);
    }

    private long getTail() {
        return UnsafeAccess.UNSAFE.getLongVolatile(this, TAIL_OFFSET);
    }
    private boolean casTail(long expect, long newValue) {
        return UnsafeAccess.UNSAFE.compareAndSwapLong(this, TAIL_OFFSET, expect, newValue);
    }

    public boolean add(final E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue is full");
    }
    private long elementOffsetInBuffer(long index) {
        return ARRAY_BASE + ((index & mask) << ELEMENT_SHIFT);
    }
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }

        long currentTail;
        for (int missCount=0;;){
        	currentTail = getTail();
    		final long wrapPoint = currentTail - capacity;
    		if (getHead() <= wrapPoint) {
				return false;
    		}
    		if(casTail(currentTail, currentTail+1)){
    			break;
    		}
    		else{
    			missCount=CAS_BACKOFF.backoff(missCount);
    		}
        }
		UnsafeAccess.UNSAFE.putOrderedObject(buffer, elementOffsetInBuffer(currentTail), e);
        return true;
    }

    public E poll() {
        final long offset = elementOffsetInBuffer(head);
        @SuppressWarnings("unchecked")
        final E e = (E) UnsafeAccess.UNSAFE.getObjectVolatile(buffer, offset);
        if (null == e) {
            return null;
        }
        UnsafeAccess.UNSAFE.putObject(buffer, offset, null);
        lazySetHead(head+1);
        return e;
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
        long currentHead = getHead();
        return getElement(currentHead);
    }

    @SuppressWarnings("unchecked")
    private E getElement(long index) {
        return (E) UnsafeAccess.UNSAFE.getObject(buffer, elementOffsetInBuffer(index));
    }


    public int size() {
        return (int) (getTail() - getHead());
    }

    public boolean isEmpty() {
        return getTail() == getHead();
    }

    public boolean contains(final Object o) {
        if (null == o) {
            return false;
        }

        for (long i = getHead(), limit = getTail(); i < limit; i++) {
            final E e = getElement(i);
            if (o.equals(e)) {
                return true;
            }
        }

        return false;
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
}
