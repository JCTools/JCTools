package org.jctools.sets;

import static org.jctools.queues.CircularArrayOffsetCalculator.calcElementOffset;
import static org.jctools.util.UnsafeRefArrayAccess.lpElement;
import static org.jctools.util.UnsafeRefArrayAccess.spElement;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jctools.util.Pow2;

public class OpenHashSet<E> extends AbstractSet<E> {
    /* current element count */
    private int size;
    /* buffer.length is a power of 2 */
    private E[] buffer;
    private int resizeThreshold;

    @SuppressWarnings("unchecked")
    public OpenHashSet(int capacity) {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        // pad data on either end with some empty slots?
        buffer = (E[]) new Object[actualCapacity];
        resizeThreshold = (int) (0.75 * actualCapacity);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(E newVal) {
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;

        final int hash = rehash(newVal.hashCode());
        final long offset = calcElementOffset(hash, mask);
        final E currVal = lpElement(buffer, offset);

        boolean result;
        if (currVal == null) {
            size++;
            spElement(buffer, offset, newVal);
            result = true;
        }
        else if (currVal.equals(newVal)) {
            result = false;
        }
        else {
            result = addSlowPath(buffer, mask, newVal, hash);
        }

        if (result && size > resizeThreshold) {
            resize();
        }
        return result;
    }

    private void addForResize(final E[] buffer, final long mask, E newVal) {
        final int hash = rehash(newVal.hashCode());
        final int limit = (int) (hash + mask);
        for (int i = hash; i <= limit; i++) {
            final long offset = calcElementOffset(i, mask);
            final E currVal = lpElement(buffer, offset);
            if (currVal == null) {
                spElement(buffer, offset, newVal);
                return;
            }
        }
    }

    private boolean addSlowPath(E[] buffer, long mask, E newVal, int hash) {
        final int limit = (int) (hash + mask);
        for (int i = hash + 1; i <= limit; i++) {
            final long offset = calcElementOffset(i, mask);
            final E currVal = lpElement(buffer, offset);
            if (currVal == null) {
                size++;
                spElement(buffer, offset, newVal);
                return true;
            }
            else if (currVal.equals(newVal)) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        final E[] oldBuffer = buffer;
        final E[] newBuffer = (E[]) new Object[oldBuffer.length * 2];
        final long mask = newBuffer.length - 1;
        int countdown = size;
        for (int i = 0; i < oldBuffer.length && countdown > 0; i++) {
            if (oldBuffer[i] != null) {
                addForResize(newBuffer, mask, oldBuffer[i]);
                countdown--;
            }
        }

        buffer = newBuffer;
    }

    @Override
    public boolean remove(Object val) {
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;
        final int hashCode = val.hashCode();
        final int hash = rehash(val.hashCode());
        final long offset = calcElementOffset(hash, mask);
        final E e = lpElement(buffer, offset);
        if (e == null) {
            return false;
        }
        else if (e.equals(val)) {
            spElement(buffer, offset, null);
            size--;
            compactValueSequenceAfterRemove(buffer, mask, offset, (int) (hash + mask), hash, hashCode);
            return true;
        }
        return removeSlowPath(val, buffer, mask, hashCode, hash);
    }

    private boolean removeSlowPath(Object val, final E[] buffer, final long mask, final int hashCode,
            final int hash) {
        final int limit = (int) (hash + mask);
        for (int i = hash + 1; i <= limit; i++) {
            final long offset = calcElementOffset(i, mask);
            final E e = lpElement(buffer, offset);
            if (e == null) {
                return false;
            }
            else if (e.equals(val)) {
                spElement(buffer, offset, null);
                size--;
                // compact same hash values
                compactValueSequenceAfterRemove(buffer, mask, offset, limit, i, hashCode);
                return true;
            }
        }
        return false;
    }

    private void compactValueSequenceAfterRemove(final E[] buffer, final long mask, long removedOffset,
            final int hashIndexLimit, int removedHashIndex, int removedHashCode) {
        for (int j = removedHashIndex + 1; j <= hashIndexLimit; j++) {
            final long offsetSrc = calcElementOffset(removedHashIndex, mask);
            final E src = lpElement(buffer, offsetSrc);
            if (src == null) {
                break;
            }
            if (removedHashCode != src.hashCode()) {
                continue;
            }
            spElement(buffer, removedOffset, src);
            spElement(buffer, offsetSrc, null);
            removedOffset = offsetSrc;
        }
    }

    private int rehash(int h) {
        return h ^ (h >>> 16);
    }

    @Override
    public boolean contains(Object needle) {
        // contains takes a snapshot of the buffer.
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;
        final int hash = rehash(needle.hashCode());
        long offset = calcElementOffset(hash, mask);
        final E e = lpElement(buffer, offset);
        if (e == null) {
            return false;
        }
        else if (e.equals(needle)) {
            return true;
        }
        return containsSlowPath(buffer, mask, hash, needle);
    }

    private boolean containsSlowPath(final E[] buffer, final long mask, final int hash, Object needle) {
        for (int i = hash + 1; i <= hash + mask; i++) {
            final long offset = calcElementOffset(i, mask);
            final E e = lpElement(buffer, offset);
            if (e == null) {
                return false;
            }
            else if (e.equals(needle)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iter<E>(this);
    }

    private final static class Iter<E> implements Iterator<E> {
        private final E[] buffer;
        private final OpenHashSet<E> set;
        private int index;
        private E nextVal = null;
        private E lastVal = null;

        public Iter(OpenHashSet<E> set) {
            this.set = set;
            this.buffer = set.buffer;
            findNextVal();
        }

        @Override
        public boolean hasNext() {
            return nextVal != null;
        }

        @Override
        public E next() {
            if (nextVal == null)
                throw new NoSuchElementException();
            E e = nextVal;
            findNextVal();
            lastVal = e;
            return e;
        }

        private void findNextVal() {
            E[] array = buffer;
            int i = index;
            E e = null;
            for (; i < array.length; i++) {
                e = array[i];
                if (e != null) {
                    nextVal = e;
                    index = i + 1;
                    return;
                }
            }
            nextVal = null;
        }

        @Override
        public void remove() {
            E e;
            if ((e = lastVal) != null) {
                lastVal = null;
                set.remove(e);
            }
        }
    }
}
