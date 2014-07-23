package org.jctools.util;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UnsafeDirectByteBuffer {
	private static final long addressOffset;
	public static final int CACHE_LINE_SIZE = 64;
	public static final int PAGE_SIZE = UnsafeAccess.UNSAFE.pageSize();
	static {
		try {
			addressOffset = UnsafeAccess.UNSAFE.objectFieldOffset(Buffer.class
			        .getDeclaredField("address"));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static long getAddress(ByteBuffer buffy) {
		return UnsafeAccess.UNSAFE.getLong(buffy, addressOffset);
	}

	/**
	 * put byte and skip position update and boundary checks
	 * 
	 * @param buffy
	 * @param b
	 */
	public static void putByte(long address, int position, byte b) {
		UnsafeAccess.UNSAFE.putByte(address + position, b);
	}

	public static void putByte(long address, byte b) {
		UnsafeAccess.UNSAFE.putByte(address, b);
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

	public static boolean isPageAligned(ByteBuffer buffy) {
		return isPageAligned(getAddress(buffy));
	}

	/**
	 * This assumes cache line is 64b
	 */
	public static boolean isCacheAligned(ByteBuffer buffy) {
		return isCacheAligned(getAddress(buffy));
	}

	public static boolean isPageAligned(long address) {
		return (address & (PAGE_SIZE - 1)) == 0;
	}

	/**
	 * This assumes cache line is 64b
	 */
	public static boolean isCacheAligned(long address) {
		return (address & (CACHE_LINE_SIZE - 1)) == 0;
	}

	public static boolean isAligned(long address, long align) {
		if (Long.bitCount(align) != 1) {
			throw new IllegalArgumentException("Alignment must be a power of 2");
		}
		return (address & (align - 1)) == 0;
	}
}
