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

import org.jctools.channels.ChannelProducer;
import org.jctools.channels.mapping.Flyweight;

/**
 * Package Scoped: not part of public API.
 *
 * @param <E> element type.
 */
final class SpscChannelProducer<E> implements ChannelProducer<E> {

    private final E element;
    private final Flyweight flyweight;
    private final SpscChannel<E> channel;

    public SpscChannelProducer(E element, Flyweight flyweight, SpscChannel<E> channel) {
        this.element = element;
        this.flyweight = flyweight;
        this.channel = channel;

        // producer owns tail and headCache
        channel.setHeadCache(0);
        channel.setTail(0);
    }

    public boolean claim() {
        final long currentTail = channel.getTailPlain();
        final long wrapPoint = currentTail - channel.capacity();
        if (channel.getHeadCache() <= wrapPoint) {
            channel.setHeadCache(channel.getHead());
            if (channel.getHeadCache() <= wrapPoint) {
                return false;
            }
        }

        channel.setTail(currentTail + 1);
        flyweight.moveTo(channel.calcElementOffset(currentTail));
        flyweight.claim();

        return true;
    }

    public E getWriter() {
        return element;
    }

    public boolean commit() {
        // TODO: figure out if this can ever fail?
        flyweight.commit();
        return true;
    }

}
