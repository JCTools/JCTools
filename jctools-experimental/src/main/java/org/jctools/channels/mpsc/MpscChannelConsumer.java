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

import org.jctools.channels.ChannelConsumer;
import org.jctools.channels.ChannelReceiver;

/**
 * Package Scoped: not part of public API.
 */
public abstract class MpscChannelConsumer<E> extends MpscOffHeapFixedSizeRingBuffer implements ChannelConsumer {

    protected final ChannelReceiver<E> receiver;

    protected long pointer;

    public MpscChannelConsumer(
            final ByteBuffer buffer,
            final int capacity,
            final int messageSize,
            final ChannelReceiver<E> receiver) {

        super(buffer, capacity, false, true, false, messageSize, null, 0);

        this.receiver = receiver;
        this.pointer = EOF;
    }

}
