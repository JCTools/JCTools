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

import java.nio.ByteBuffer;

import org.jctools.channels.ChannelProducer;

/**
 * Package Scoped: not part of public API.
 *
 * @param <E> element type.
 */
public abstract class MpscChannelProducer<E> extends MpscOffHeapFixedSizeRingBuffer implements ChannelProducer<E> {

    protected long pointer;

    public MpscChannelProducer(
        final ByteBuffer buffer,
        final int capacity,
        final int messageSize) {

        super(buffer, capacity, true, false, true, messageSize, null, 0);
        pointer = EOF;
    }

    public final boolean claim() {
        pointer = writeAcquire();
        return pointer != EOF;
    }

    public final boolean commit() {
        if (pointer == EOF)
            return false;

        writeRelease(pointer);
        return true;
    }

}
