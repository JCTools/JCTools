package org.jctools.sets;

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

public class OpenHashSet<E> extends AbstractSet<E> {
    private static final long REF_ARRAY_BASE;
    private static final int REF_ELEMENT_SHIFT;
    private static final Object TOMBSTONE = new Object();
    static {
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size");
        }
        // Including the buffer pad in the array base offset
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class);
    }
    private int occupancy;
    /* current element count */
    private int size;
    /* buffer.length -1 */
    private int mask;
    /* buffer.length is a power of 2 */
    private E[] buffer;
    private int resizeThreshold;

    @SuppressWarnings("unchecked")
    public OpenHashSet(int capacity) {
        int actualCapacity = Pow2.roundToPowerOfTwo(capacity);
        mask = actualCapacity - 1;
        // pad data on either end with some empty slots.
        buffer = (E[]) new Object[actualCapacity];
        resizeThreshold = (int) (0.75 * actualCapacity);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(E newVal) {
        if (newVal == null)
            throw new IllegalArgumentException();
        int hash = calcHash(newVal);
        long offset = calcOffset(hash);
        E e = lpElement(offset);
        if (replaceElement(e, newVal, offset)) {
            return true;
        } else if (e.equals(newVal)) {
            return false;
        }
        return addSlowPath(newVal, hash);
    }

    protected int calcHash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    private boolean addSlowPath(E newVal, int hash) {
        long offset;
        E e;
        for (int i = hash + 1; i <= hash + mask; i++) {
            offset = calcOffset(i);
            e = lpElement(offset);
            if (replaceElement(e, newVal, offset)) {
                return true;
            } else if (e.equals(newVal)) {
                return false;
            }
        }
        return false;
    }

    private boolean replaceElement(E oldVal, E newVal, long offset) {
        if (oldVal == null) {
            occupancy++;
        } else if (oldVal != TOMBSTONE) {
            return false;
        }
        spElement(offset, newVal);
        size++;
        if(size > resizeThreshold) {
            resize();
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void resize() {
        E[] oldBuffer = buffer;
        int newCapacity = oldBuffer.length * 2;
        buffer = (E[]) new Object[newCapacity];
        mask = buffer.length-1;
        resizeThreshold = (int) (0.75 * newCapacity);
        int countdown = size;
        size = 0;
        occupancy = 0;
        for(int i=0;i < oldBuffer.length && countdown > 0;i++) {
            if(oldBuffer[i] != null && oldBuffer[i] != TOMBSTONE) {
                add(oldBuffer[i]);
                countdown--;
            }
        }
    }

    @Override
    public boolean remove(Object val) {
        if(val == null) {
            return false;
        }
        int hash = calcHash(val);
        long offset = calcOffset(hash);
        E e = lpElement(offset);
        if (e == null) {
            return false;
        } else if (e.equals(val)) {
            // we can null out chains of TOMBSTONES followed by a null
            if(lpElement(calcOffset(hash + 1)) == null) {
                spElement(offset, null);
                while(lpElement(offset = calcOffset(--hash)) == TOMBSTONE) {
                    spElement(offset, null);
                }
            }
            else {
                spElement(offset, TOMBSTONE);
            }
            size--;
            return true;
        }
        for (int i = hash + 1; i <= hash + mask; i++) {
            offset = calcOffset(i);
            e = lpElement(offset);
            if (e == null) {
                return false;
            } else if (e.equals(val)) {
                spElement(offset, TOMBSTONE);
                size--;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(Object val) {
        if(val == null) {
            return false;
        }
        int hash = calcHash(val);
        long offset = calcOffset(hash);
        E e = lpElement(offset);
        if (e == null) {
            return false;
        } else if (e.equals(val)) {
            return true;
        }
        for (int i = hash + 1; i <= hash + mask; i++) {
            offset = calcOffset(i);
            e = lpElement(offset);
            if (e == null) {
                return false;
            } else if (e.equals(val)) {
                return true;
            }
        }
        return false;
    }

    private long calcOffset(long index) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    private void spElement(long offset, Object e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    protected final E lpElement(long offset) {
        return (E) UNSAFE.getObject(buffer, offset);
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
                if (e != null && e != TOMBSTONE) {
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
            if((e = lastVal) != null) {
                lastVal = null;
                set.remove(e);
            }
        }
    }
}
