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
import org.jctools.channels.ChannelReceiver;
import org.jctools.channels.mapping.Flyweight;

/**
 * Package Scoped: not part of public API.
 */
final class SpscChannelConsumer<E> implements ChannelConsumer {

    private final E element;
    private final Flyweight flyweight;
    private final ChannelReceiver<E> receiver;
    private final SpscChannel<E> channel;

    SpscChannelConsumer(
            final E element,
            final Flyweight flyweight,
            final ChannelReceiver<E> receiver,
            final SpscChannel<E> channel) {

        this.element = element;
        this.flyweight = flyweight;
        this.receiver = receiver;
        this.channel = channel;

        // consumer owns head and tailCache
        channel.setTailCache(0);
        channel.setHead(0);
    }

    public boolean read() {
        final long currentHead = channel.getHeadPlain();
        if (currentHead >= channel.getTailCache()) {
            channel.setTailCache(channel.getTail());
            if (currentHead >= channel.getTailCache()) {
                return false;
            }
        }

        final long offset = channel.calcElementOffset(currentHead);
        flyweight.moveTo(offset);
        if (!flyweight.isCommitted()) {
            return false;
        }

        receiver.accept(element);
        channel.setHead(currentHead + 1);
        return true;
    }

}
