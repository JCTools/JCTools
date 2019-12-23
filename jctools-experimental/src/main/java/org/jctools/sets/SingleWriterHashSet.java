package org.jctools.sets;

import org.jctools.util.Pow2;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

public class SingleWriterHashSet<E> extends AbstractSet<E> {
    /* current element count */
    private int size;
    private long modCount;

    /* buffer.length is a power of 2 */
    private E[] buffer;
    private int resizeThreshold;

    @SuppressWarnings("unchecked")
    public SingleWriterHashSet(int capacity) {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        // pad data on either end with some empty slots?
        buffer = (E[]) new Object[actualCapacity];
        resizeThreshold = (int) (0.75 * buffer.length);
    }

    @Override
    public int size() {
        // size read needs to by volatile so that changes are visible
        return UNSAFE.getIntVolatile(this, SIZE_OFFSET);
    }

    @Override
    public boolean add(E newVal) {
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;

        final int hash = rehash(newVal.hashCode());
        final long offset = calcCircularRefElementOffset(hash, mask);
        final E currVal = lpRefElement(buffer, offset);

        boolean result;
        if (currVal == null) {
            size++;
            soRefElement(buffer, offset, newVal);
            result = true;
        }
        else {
            result = !newVal.equals(currVal) && addSlowPath(buffer, mask, newVal, hash);
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
            final long offset = calcCircularRefElementOffset(i, mask);
            final E currVal = lpRefElement(buffer, offset);
            if (currVal == null) {
                soRefElement(buffer, offset, newVal);
                return;
            }
        }
    }

    private boolean addSlowPath(E[] buffer, long mask, E newVal, int hash) {
        final int limit = (int) (hash + mask);
        for (int i = hash + 1; i <= limit; i++) {
            final long offset = calcCircularRefElementOffset(i, mask);
            final E currVal = lpRefElement(buffer, offset);
            if (currVal == null) {
                size++;
                soRefElement(buffer, offset, newVal);
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
        final long mask = newBuffer.length - 1;
        int countdown = size;
        for (int i = 0; i < oldBuffer.length && countdown > 0; i++) {
            if (oldBuffer[i] != null) {
                addForResize(newBuffer, mask, oldBuffer[i]);
                countdown--;
            }
        }
        // store ordered
        soBuffer(newBuffer);
        resizeThreshold = (int) (0.75 * buffer.length);
    }

    @Override
    public boolean remove(Object val) {
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;
        final int hash = rehash(val.hashCode());
        final long offset = calcCircularRefElementOffset(hash, mask);
        final E e = lpRefElement(buffer, offset);
        if (e == null) {
            return false;
        }
        else if (val.equals(e)) {
            size--;
            if (lpRefElement(buffer, calcCircularRefElementOffset(hash + 1, mask)) == null) {
                soRefElement(buffer, offset, null);
            }
            else {
                compactAndRemove(buffer, mask, hash);
            }
            return true;
        }
        return removeSlowPath(val, buffer, mask, hash);
    }

    private boolean removeSlowPath(Object val, E[] buffer, long mask, int hash) {
        final int limit = (int) (hash + mask);
        for (int searchIndex = hash + 1; searchIndex <= limit; searchIndex++) {
            final long offset = calcCircularRefElementOffset(searchIndex, mask);
            final E e = lpRefElement(buffer, offset);
            if (e == null) {
                return false;
            }
            else if (val.equals(e)) {
                size--;
                if (lpRefElement(buffer, calcCircularRefElementOffset(searchIndex + 1, mask)) == null) {
                    soRefElement(buffer, offset, null);
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
    private void compactAndRemove(final E[] buffer, final long mask, int removeHashIndex) {
        // remove(9a): [9a,9b,10a,9c,10b,11a,null] -> [9b,10a,9c,10b,11a,null,null]
        removeHashIndex = (int) (removeHashIndex & mask);
        int j = removeHashIndex;
        // every compaction is guarded by two mod count increments: one before and one after actual compaction
        UNSAFE.putOrderedLong(this, MC_OFFSET, modCount + 1);
        while (true) {
            int k;
            E slotJ;
            // skip elements which belong where they are
            do {
                // j := (j+1) modulo num_slots
                j = (int) ((j + 1) & mask);
                slotJ = lpRefElement(buffer, calcCircularRefElementOffset(j, mask));
                // if slot[j] is unoccupied exit
                if (slotJ == null) {
                    // delete last duplicate slot
                    soRefElement(buffer, calcCircularRefElementOffset(removeHashIndex, mask), null);
                    UNSAFE.putOrderedLong(this, MC_OFFSET, modCount + 1);
                    return;
                }

                // k := hash(slot[j].key) modulo num_slots
                k = (int) (rehash(slotJ.hashCode()) & mask);
                // determine if k lies cyclically in [i,j]
                // |    i.k.j |
                // |....j i.k.| or |.k..j i...|
            }
            while ( (removeHashIndex <= j) ?
                    ((removeHashIndex < k) && (k <= j)) :
                    ((removeHashIndex < k) || (k <= j)) );
            // slot[removeHashIndex] := slot[j]
            soRefElement(buffer, calcCircularRefElementOffset(removeHashIndex, mask), slotJ);
            // removeHashIndex := j
            removeHashIndex = j;
        }
    }

    @Override
    public String toString() {
        return "SingleWriterHashSet [size=" + size + ", buffer=" + Arrays.toString(buffer)
                + ", resizeThreshold=" + resizeThreshold + "]";
    }

    private int rehash(int h) {
        return h ^ (h >>> 16);
    }

    @Override
    public boolean contains(Object needle) {
        while (true) {
            long mc = UNSAFE.getLongVolatile(this, MC_OFFSET);
            boolean result = containsImpl(needle);
            long newMc = UNSAFE.getLongVolatile(this, MC_OFFSET);
            if ((newMc & 1) == 0 && mc == newMc) {
                return result;
            }
        }
    }

    private boolean containsImpl(Object needle) {
        // contains takes a snapshot of the buffer.
        final E[] buffer = this.buffer;
        final long mask = buffer.length - 1;
        final int hash = rehash(needle.hashCode());
        long offset = calcCircularRefElementOffset(hash, mask);
        final E e = lvRefElement(buffer, offset);
        if (e == null) {
            return false;
        }
        else if (needle.equals(e)) {
            return true;
        }
        return containsSlowPath(buffer, mask, hash, needle);
    }

    private boolean containsSlowPath(final E[] buffer, final long mask, final int hash, Object needle) {
        for (int i = hash + 1; i <= hash + mask; i++) {
            final long offset = calcCircularRefElementOffset(i, mask);
            final E e = lvRefElement(buffer, offset);
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
        private final SingleWriterHashSet<E> set;
        private int nextValIndex;
        private int lastValIndex;
        private E nextVal = null;
        private E lastVal = null;

        public Iter(SingleWriterHashSet<E> set) {
            this.set = set;
            this.buffer = set.lvBuffer();
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
            for (; i < array.length; i++) {
                E e = array[i];
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
            if (lastVal == null) {
                throw new IllegalStateException();
            }

            set.remove(lastVal);
            lastVal = null;
            nextValIndex = lastValIndex - 1;
            findNextVal();
        }
    }

    private final static long BUFFER_OFFSET = fieldOffset(SingleWriterHashSet.class, "buffer");
    private final static long SIZE_OFFSET = fieldOffset(SingleWriterHashSet.class,"size");
    private final static long MC_OFFSET = fieldOffset(SingleWriterHashSet.class, "modCount");

    private void soBuffer(final E[] buffer) {
        UNSAFE.putOrderedObject(this, BUFFER_OFFSET, buffer);
    }

    @SuppressWarnings("unchecked")
    private E[] lvBuffer() {
        return (E[]) UNSAFE.getObjectVolatile(this, BUFFER_OFFSET);
    }
}