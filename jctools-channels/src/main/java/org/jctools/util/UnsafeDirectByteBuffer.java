package org.jctools.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static org.jctools.util.UnsafeAccess.UNSAFE;

public class UnsafeDirectByteBuffer
{
	private static final long addressOffset = UnsafeAccess.fieldOffset(Buffer.class, "address");

	public static long getAddress(ByteBuffer buffy) {
		return UNSAFE.getLong(buffy, addressOffset);
	}

	public static ByteBuffer allocateAlignedByteBuffer(int capacity, long align) {
		if (Long.bitCount(align) != 1) {
			throw new IllegalArgumentException("Alignment must be a power of 2");
		}
		ByteBuffer buffy = ByteBuffer.allocateDirect((int) (capacity + align));
		return alignedSlice(capacity, align, buffy);
	}

	public static ByteBuffer alignedSlice(int capacity, long align,
	        ByteBuffer buffy) {
		long address = getAddress(buffy);
		if ((address & (align - 1)) == 0) {
			buffy.limit(capacity);
			return buffy.slice();
		} else {
			int newPosition = (int) (align - (address & (align - 1)));
			if (newPosition + capacity > buffy.capacity()) {
				throw new IllegalArgumentException("it's impossible!");
			}
			int oldPosition = buffy.position();
			buffy.position(newPosition);
			int newLimit = newPosition + capacity;
			buffy.limit(newLimit);
			ByteBuffer slice = buffy.slice();
			buffy.position(oldPosition);
			return slice;
		}
	}
}
