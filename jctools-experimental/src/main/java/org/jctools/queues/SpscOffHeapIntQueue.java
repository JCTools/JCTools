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
package org.jctools.queues;

import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;
import org.jctools.util.UnsafeDirectByteBuffer;

import java.nio.ByteBuffer;
import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.UnsafeDirectByteBuffer.*;

public final class SpscOffHeapIntQueue extends AbstractQueue<Integer> {
	public final static byte PRODUCER = 1;
	public final static byte CONSUMER = 2;
	public static final int INT_ELEMENT_SCALE = 2 + Integer.getInteger("sparse.shift", 0);
	// 24b,8b,8b,24b | pad | 24b,8b,8b,24b | pad
	private final ByteBuffer buffy;
	private final long headAddress;
	private final long tailCacheAddress;
	private final long tailAddress;
	private final long headCacheAddress;

	private final int capacity;
	private final int mask;
	private final long arrayBase;

	public SpscOffHeapIntQueue(final int capacity) {
		this(allocateAlignedByteBuffer(
		        getRequiredBufferSize(capacity),
		        PortableJvmInfo.CACHE_LINE_SIZE),
		        Pow2.roundToPowerOfTwo(capacity),(byte)(PRODUCER | CONSUMER));
	}

	public static int getRequiredBufferSize(final int capacity) {
		int p2Capacity = Pow2.roundToPowerOfTwo(capacity);
		if (p2Capacity > (Pow2.MAX_POW2 >> INT_ELEMENT_SCALE))
			throw new IllegalArgumentException("capacity exceeds max buffer capacity: " + (Pow2.MAX_POW2 >> INT_ELEMENT_SCALE));
		return 4 * PortableJvmInfo.CACHE_LINE_SIZE + (p2Capacity << INT_ELEMENT_SCALE);
    }

	/**
	 * This is to be used for an IPC queue with the direct buffer used being a memory
	 * mapped file.
	 *
	 * @param buff
	 * @param capacity
	 * @param viewMask
	 */
	public SpscOffHeapIntQueue(final ByteBuffer buff,
			final int capacity, byte viewMask) {
		this.capacity = Pow2.roundToPowerOfTwo(capacity);
		buffy = alignedSlice(4 * PortableJvmInfo.CACHE_LINE_SIZE + (this.capacity << INT_ELEMENT_SCALE),
		        PortableJvmInfo.CACHE_LINE_SIZE, buff);

		long alignedAddress = UnsafeDirectByteBuffer.getAddress(buffy);

		headAddress = alignedAddress;
		tailCacheAddress = headAddress + 8;
		tailAddress = headAddress + 2l * PortableJvmInfo.CACHE_LINE_SIZE;
		headCacheAddress = tailAddress + 8;
		arrayBase = alignedAddress + 4l * PortableJvmInfo.CACHE_LINE_SIZE;
		// producer owns tail and headCache
		if((viewMask & PRODUCER) == PRODUCER){
    		setHeadCache(0);
    		setTail(0);
		}
		// consumer owns head and tailCache
		if((viewMask & CONSUMER) == CONSUMER){
	    	setTailCache(0);
			setHead(0);
		}
		mask = this.capacity - 1;
	}

	public boolean offer(final Integer e) {
		if (null == e) {
			throw new NullPointerException("Null is not a valid element");
		}

		final long currentTail = getTailPlain();
		final long wrapPoint = currentTail - capacity;
		if (getHeadCache() <= wrapPoint) {
			setHeadCache(getHead());
			if (getHeadCache() <= wrapPoint) {
				return false;
			}
		}

		long offset = calcElementOffset(currentTail);
		UnsafeAccess.UNSAFE.putInt(offset, e);

		setTail(currentTail + 1);

		return true;
	}
	public boolean offerInt(final int e) {
        final long currentTail = getTailPlain();
        final long wrapPoint = currentTail - capacity;
        if (getHeadCache() <= wrapPoint) {
            setHeadCache(getHead());
            if (getHeadCache() <= wrapPoint) {
                return false;
            }
        }

        long offset = calcElementOffset(currentTail);
        UnsafeAccess.UNSAFE.putInt(offset, e);

        setTail(currentTail + 1);

        return true;
    }

	public Integer poll() {
		int i = pollInt();
		if(i == Integer.MIN_VALUE) {
		    return null;
		}
		return i;
	}

	public int pollInt() {
		final long currentHead = getHeadPlain();
		if (currentHead >= getTailCache()) {
			setTailCache(getTail());
			if (currentHead >= getTailCache()) {
				return Integer.MIN_VALUE;
			}
		}

		final long offset = calcElementOffset(currentHead);
		final int e = UnsafeAccess.UNSAFE.getInt(offset);
		setHead(currentHead + 1);
		return e;
    }
    private long calcElementOffset(final long currentHead) {
        return arrayBase + ((currentHead & mask) << INT_ELEMENT_SCALE);
    }

	public Integer peek() {
	    int i = peekInt();
        if(i == Integer.MIN_VALUE) {
            return null;
        }
        return i;
    }

    public int peekInt() {
        final long currentHead = getHeadPlain();
        if (currentHead >= getTailCache()) {
            setTailCache(getTail());
            if (currentHead >= getTailCache()) {
                return Integer.MIN_VALUE;
            }
        }

        final long offset = calcElementOffset(currentHead);
        return UnsafeAccess.UNSAFE.getInt(offset);
    }

	public int size() {
		return (int) (getTail() - getHead());
	}

	public boolean isEmpty() {
		return getTail() == getHead();
	}

	public Iterator<Integer> iterator() {
		throw new UnsupportedOperationException();
	}

	private long getHeadPlain() {
		return UnsafeAccess.UNSAFE.getLong(null, headAddress);
	}
	private long getHead() {
		return UnsafeAccess.UNSAFE.getLongVolatile(null, headAddress);
	}

	private void setHead(final long value) {
		UnsafeAccess.UNSAFE.putOrderedLong(null, headAddress, value);
	}

	private long getTailPlain() {
		return UnsafeAccess.UNSAFE.getLong(null, tailAddress);
	}
	private long getTail() {
		return UnsafeAccess.UNSAFE.getLongVolatile(null, tailAddress);
	}

	private void setTail(final long value) {
		UnsafeAccess.UNSAFE.putOrderedLong(null, tailAddress, value);
	}

	private long getHeadCache() {
		return UnsafeAccess.UNSAFE.getLong(null, headCacheAddress);
	}

	private void setHeadCache(final long value) {
		UnsafeAccess.UNSAFE.putLong(headCacheAddress, value);
	}

	private long getTailCache() {
		return UnsafeAccess.UNSAFE.getLong(null, tailCacheAddress);
	}

	private void setTailCache(final long value) {
		UnsafeAccess.UNSAFE.putLong(tailCacheAddress, value);
	}

}
