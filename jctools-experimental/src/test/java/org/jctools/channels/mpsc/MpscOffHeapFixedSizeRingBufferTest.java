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
package org.jctools.channels.mpsc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.jctools.channels.mpsc.MpscOffHeapFixedSizeRingBuffer;
import org.jctools.util.UnsafeAccess;
import org.junit.Test;

public class MpscOffHeapFixedSizeRingBufferTest {

	@Test
	public void test() {
		MpscOffHeapFixedSizeRingBuffer rb = new MpscOffHeapFixedSizeRingBuffer(1024, 31);
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
		
		long writeOffset = rb.writeAcquire();
		assertNotEquals(rb.EOF, writeOffset);
		long fieldOffset = writeOffset+MpscOffHeapFixedSizeRingBuffer.MESSAGE_INDICATOR_SIZE;
		UnsafeAccess.UNSAFE.putInt(fieldOffset,1);
		UnsafeAccess.UNSAFE.putLong(fieldOffset+4,1);
		// blah blah, not writing the rest
		
		rb.writeRelease(writeOffset);
		assertEquals(1, rb.size());
		assertTrue(!rb.isEmpty());
		long readOffset = rb.readAcquire();
		fieldOffset = readOffset + MpscOffHeapFixedSizeRingBuffer.MESSAGE_INDICATOR_SIZE;
		assertNotEquals(rb.EOF, readOffset);
		assertEquals(writeOffset, readOffset);
		assertEquals(1, UnsafeAccess.UNSAFE.getInt(fieldOffset));
		assertEquals(1L, UnsafeAccess.UNSAFE.getLong(fieldOffset+4));
		rb.readRelease(readOffset);
		
		assertEquals(0, rb.size());
		assertTrue(rb.isEmpty());
		assertEquals(rb.EOF, rb.readAcquire());
	}

}
