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
package org.jctools.channels.spsc;

import org.jctools.util.UnsafeAccess;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpscOffHeapFixedSizeRingBufferTest {

	@Test
	public void test() {
		SpscOffHeapFixedSizeRingBuffer rb = new SpscOffHeapFixedSizeRingBuffer(1024, 31);
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
		
		long writeOffset = rb.writeAcquire();
		assertNotEquals(0, writeOffset);
		long fieldOffset = writeOffset+SpscOffHeapFixedSizeRingBuffer.MESSAGE_INDICATOR_SIZE;
		UnsafeAccess.UNSAFE.putInt(fieldOffset,1);
		UnsafeAccess.UNSAFE.putLong(fieldOffset+4,1);
		// blah blah, not writing the rest
		
		rb.writeRelease(writeOffset);
		assertEquals(1, rb.size());
		assertTrue(!rb.isEmpty());
		long readOffset = rb.readAcquire();
		fieldOffset = readOffset + SpscOffHeapFixedSizeRingBuffer.MESSAGE_INDICATOR_SIZE;
		assertEquals(writeOffset, readOffset);
		assertEquals(1, UnsafeAccess.UNSAFE.getInt(fieldOffset));
		assertEquals(1L, UnsafeAccess.UNSAFE.getLong(fieldOffset+4));
		rb.readRelease(readOffset);
		
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
	}

}
