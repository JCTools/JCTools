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
package org.jctools.channels.mapping;

import org.jctools.util.UnsafeAccess;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MapperTest {

    private static final int EXAMPLE_SIZE_IN_BYTES = 13;

    private long startAddress;
    private Mapper<Example> mapper;

    @Before
    public void malloc() {
        startAddress = UnsafeAccess.UNSAFE.allocateMemory(EXAMPLE_SIZE_IN_BYTES * 2);
        mapper = new Mapper<>(Example.class, true);
    }

    @After
    public void free() {
        UnsafeAccess.UNSAFE.freeMemory(startAddress);
    }

    @Test
    public void shouldUnderstandInterfaceFields() {
        assertEquals(EXAMPLE_SIZE_IN_BYTES, mapper.getSizeInBytes());
        Example example = mapper.newFlyweight(startAddress);
        assertNotNull(example);
        assertTrue(example instanceof Flyweight);
    }

    @Test
    public void shouldBeAbleToReadAndWriteData() {
        Example writer = mapper.newFlyweight(startAddress);
        Example reader = mapper.newFlyweight(startAddress);

        writer.setFoo(5);
        assertEquals(5, reader.getFoo());

        writer.setBar(6L);
        assertEquals(6L, reader.getBar());
    }

    @Test
    public void shouldBeAbleToMoveFlyweights() {
        Example writer = mapper.newFlyweight(startAddress);
        Example reader = mapper.newFlyweight(startAddress);

        Flyweight writeCursor = (Flyweight) writer;
        Flyweight readCursor = (Flyweight) reader;

        writeCursor.moveTo(startAddress + EXAMPLE_SIZE_IN_BYTES);
        readCursor.moveTo(startAddress + EXAMPLE_SIZE_IN_BYTES);

        writer.setFoo(5);
        assertEquals(5, reader.getFoo());

        writer.setBar(6L);
        assertEquals(6L, reader.getBar());
    }

    // ---------------------------------------------------

    public static interface Example {

        int getFoo();

        void setFoo(int value);

        long getBar();

        void setBar(long value);

    }

}
