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

import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelProducer;
import org.jctools.channels.ChannelReceiver;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;


public class SpscChannelTest {

    private static final int REQUESTED_CAPACITY = 8;
    private static final int MAXIMUM_CAPACITY = 16;

    private final ByteBuffer buffer = ByteBuffer.allocateDirect(128 * 1024);
    private final SpscChannel<Example> channel = new SpscChannel<Example>(buffer, REQUESTED_CAPACITY, Example.class);
    private final ChannelProducer<Example> producer = channel.producer();

    @Test
    public void shouldKnowItsCapacity() {
        assertEquals(REQUESTED_CAPACITY, channel.requestedCapacity());
        assertEquals(MAXIMUM_CAPACITY, channel.maximumCapacity());
    }

    @Test
    public void shouldInitiallyBeEmpty() {
        assertEmpty();
    }

    @Test
    public void shouldWriteAnObject() {
        assertTrue(producer.claim());

        Example writer = producer.currentElement();
        writer.setFoo(5);
        writer.setBar(10L);
        assertTrue(producer.commit());
        assertSize(1);
    }

    @Test
    public void shouldReadAnObject() {
        ChannelConsumer consumer = newConsumer();

        shouldWriteAnObject();

        assertTrue(consumer.read());
        assertEmpty();
    }

    @Test
    public void shouldNotReadFromEmptyChannel() {
        ChannelConsumer consumer = newConsumer();

        assertEmpty();
        assertFalse(consumer.read());
    }

    @Test
    public void shouldNotReadUnCommittedMessages() {
        ChannelConsumer consumer = newConsumer();

        assertTrue(producer.claim());

        Example writer = producer.currentElement();
        writer.setBar(10L);

        assertFalse(consumer.read());
    }

    @Test
    public void shouldNotOverrunBuffer() {
        for (int i = 0; i < REQUESTED_CAPACITY; i++) {
            assertTrue(producer.claim());
            assertTrue(producer.commit());
        }

        for (int i = REQUESTED_CAPACITY; i < MAXIMUM_CAPACITY; i++) {
            // Unknown what happens here.
            producer.claim();
            producer.commit();
        }

        assertFalse(producer.claim());
        assertTrue(channel.size() >= REQUESTED_CAPACITY);
        assertTrue(channel.size() <= MAXIMUM_CAPACITY);
    }

    private void assertSize(int expectedSize) {
        assertEquals(expectedSize, channel.size());
    }

    private ChannelConsumer newConsumer() {
        return channel.consumer(new ChannelReceiver<Example>() {
            public void accept(Example element) {
                assertEquals(10L, element.getBar());
                assertEquals(5, element.getFoo());
            }
        });
    }

    private void assertEmpty() {
        assertTrue(channel.isEmpty());
    }

    // ---------------------------------------------------

    public interface Example {

        int getFoo();

        void setFoo(int value);

        long getBar();

        void setBar(long value);

    }
}
