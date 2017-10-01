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
package org.jctools.channels;

/**
 * A producer used by a single thread for writing into a channel.
 *
 * @param <E> element type
 */
public interface ChannelProducer<E> {

    /**
     * Attempt to claim the next element in the channel.
     *
     * @see this#commit()
     * @return true if claimed, false if there is insufficient space in the channel.
     */
    boolean claim();

    /**
     * Gets the flyweight to write via.
     *
     * @return the flyweight object to write to
     */
    E currentElement();

    /**
     *
     * @see this#claim()
     * @return true if
     */
    boolean commit();

}
