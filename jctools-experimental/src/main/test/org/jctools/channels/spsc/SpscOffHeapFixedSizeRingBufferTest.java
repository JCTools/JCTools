package org.jctools.channels.spsc;

import static org.junit.Assert.*;

import org.jctools.util.UnsafeAccess;
import org.junit.Test;

public class SpscOffHeapFixedSizeRingBufferTest {

	@Test
	public void test() {
		SpscOffHeapFixedSizeRingBuffer rb = new SpscOffHeapFixedSizeRingBuffer(1024, 31);
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
		
		long writeOffset = rb.writeAcquire();
		assertNotEquals(0, writeOffset);
		UnsafeAccess.UNSAFE.putInt(writeOffset+1,1);
		UnsafeAccess.UNSAFE.putLong(writeOffset+5,1);
		// blah blah, not writing the rest
		
		rb.writeRelease(writeOffset);
		assertEquals(1, rb.size());
		assertTrue(!rb.isEmpty());
		long readOffset = rb.readAcquire();
		assertEquals(writeOffset, readOffset);
		assertEquals(1, UnsafeAccess.UNSAFE.getInt(readOffset+1));
		assertEquals(1L, UnsafeAccess.UNSAFE.getLong(readOffset+5));
		rb.readRelease(readOffset);
		
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
	}

}
