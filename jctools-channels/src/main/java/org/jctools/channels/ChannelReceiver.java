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
 * The receiver interface is implemented by end users in order to receiver data from the channel.
 *
 * @param <E> element type
 */
public interface ChannelReceiver<E> {

    /**
     * New elements are passed into this method.
     *
     * NB: the element is only readable for the lifecycle of this callback.
     *
     * @param element the element which is being accepted by the consumer.
     */
    void accept(E element);

}
