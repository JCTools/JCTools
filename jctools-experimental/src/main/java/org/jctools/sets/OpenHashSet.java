package org.jctools.sets;

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
        resizeThreshold = (int) (0.75 * buffer.length);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(E newVal) {
        final E[] buffer = this.buffer;
        final int mask = buffer.length - 1;

        final int hash = rehash(newVal.hashCode());
        final int offset = hash & mask;
        final E currVal = buffer[offset];

        boolean result;
        if (currVal == null) {
            size++;
            buffer[offset] = newVal;
            result = true;
        } else {
            result = !newVal.equals(currVal) && addSlowPath(buffer, mask, newVal, hash);
        }

        if (result && size > resizeThreshold) {
            resize();
        }
        return result;
    }

    private void addForResize(final E[] buffer, final int mask, E newVal) {
        final int hash = rehash(newVal.hashCode());
        final int limit = hash + mask;
        for (int i = hash; i <= limit; i++) {
            final int offset = i & mask;
            final E currVal = buffer[offset];
            if (currVal == null) {
                buffer[offset] = newVal;
                return;
            }
        }
    }

    private boolean addSlowPath(E[] buffer, int mask, E newVal, int hash) {
        final int limit = hash + mask;
        for (int i = hash + 1; i <= limit; i++) {
            final int offset = i & mask;
            final E currVal = buffer[offset];
            if (currVal == null) {
                size++;
                buffer[offset] = newVal;
                return true;
            }
            else if (newVal.equals(currVal)) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        final E[] oldBuffer = buffer;
        final E[] newBuffer = (E[]) new Object[oldBuffer.length * 2];
        final int mask = newBuffer.length - 1;
        int countdown = size;
        for (int i = 0; i < oldBuffer.length && countdown > 0; i++) {
            if (oldBuffer[i] != null) {
                addForResize(newBuffer, mask, oldBuffer[i]);
                countdown--;
            }
        }

        buffer = newBuffer;
        resizeThreshold = (int) (0.75 * buffer.length);
    }

    @Override
    public boolean remove(Object val) {
        final E[] buffer = this.buffer;
        final int mask = buffer.length - 1;
        final int hash = rehash(val.hashCode());
        final int offset = hash & mask;
        final E e = buffer[offset];
        if (e == null) {
            return false;
        }
        else if (val.equals(e)) {
            size--;
            if (buffer[(hash + 1) & mask] == null) {
                buffer[offset] = null;
            }
            else {
                compactAndRemove(buffer, mask, hash);
            }
            return true;
        }
        return removeSlowPath(val, buffer, mask, hash);
    }

    private boolean removeSlowPath(Object val, final E[] buffer, final int mask, final int hash) {
        final int limit = hash + mask;
        for (int searchIndex = hash + 1; searchIndex <= limit; searchIndex++) {
            final int offset = searchIndex & mask;
            final E e = buffer[offset];
            if (e == null) {
                return false;
            }
            else if (val.equals(e)) {
                size--;
                if (buffer[(searchIndex + 1) & mask] == null) {
                    buffer[offset] = null;
                }
                else {
                    compactAndRemove(buffer, mask, searchIndex);
                }
                return true;
            }
        }
        return false;
    }

    /*
     * implemented as per wiki suggested algo with minor adjustments.
     */
    private void compactAndRemove(final E[] buffer, final int mask, int removeHashIndex) {
        // remove(9a): [9a,9b,10a,9c,10b,11a,null] -> [9b,9c,10a,10b,null,11a,null]
        removeHashIndex = removeHashIndex & mask;
        int j = removeHashIndex;
        while (true) {
            int k;
            E slotJ;
            // skip elements which belong where they are
            do {
                // j := (j+1) modulo num_slots
                j = (j + 1) & mask;
                slotJ = buffer[j];
                // if slot[j] is unoccupied exit
                if (slotJ == null) {
                    // delete last duplicate slot
                    buffer[removeHashIndex] = null;
                    return;
                }

                // k := hash(slot[j].key) modulo num_slots
                k = rehash(slotJ.hashCode()) & mask;
                // determine if k lies cyclically in [i,j]
                // |    i.k.j |
                // |....j i.k.| or  |.k..j i...|
            }
            while ( (removeHashIndex <= j) ?
                    ((removeHashIndex < k) && (k <= j)) :
                    ((removeHashIndex < k) || (k <= j)) );
            // slot[removeHashIndex] := slot[j]
            buffer[removeHashIndex] = slotJ;
            // removeHashIndex := j
            removeHashIndex = j;
        }
    }

    private int rehash(int h) {
        return h ^ (h >>> 16);
    }

    @Override
    public boolean contains(Object needle) {
        // contains takes a snapshot of the buffer.
        final E[] buffer = this.buffer;
        final int mask = buffer.length - 1;
        final int hash = rehash(needle.hashCode());
        final E e = buffer[hash & mask];
        if (e == null) {
            return false;
        }
        else if (needle.equals(e)) {
            return true;
        }
        return containsSlowPath(buffer, mask, hash, needle);
    }

    private boolean containsSlowPath(final E[] buffer, final int mask, final int hash, Object needle) {
        for (int i = hash + 1; i <= hash + mask; i++) {
            final E e = buffer[i & mask];
            if (e == null) {
                return false;
            }
            else if (needle.equals(e)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return new Iter<E>(this);
    }

    private static class Iter<E> implements Iterator<E> {
        private final E[] buffer;
        private final OpenHashSet<E> set;
        private int nextValIndex;
        private int lastValIndex;
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
            lastValIndex = nextValIndex;
            findNextVal();
            lastVal = e;
            return e;
        }

        private void findNextVal() {
            E[] array = buffer;
            int i = nextValIndex;
            E e = null;
            for (; i < array.length; i++) {
                e = array[i];
                if (e != null) {
                    nextVal = e;
                    nextValIndex = i + 1;
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
                nextValIndex = lastValIndex - 1;
                findNextVal();
            }
        }
    }
}
