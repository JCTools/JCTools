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
package org.jctools.channels.proxy;

/**
 * A minimal top level queue interface which allows producer/consumers access via separate interfaces.
 *
 * Channels may determine their capacity themselves with additional slack or resize themselves as powers of two etc.
 * Consequently instead of having a definite concept of a "capacity" channels have a requested capacity and a maximum
 * capacity. The requested capacity is the capacity requested when you create the channel and the maximum capacity is the
 * size that the channel has resized itself up to.
 *
 * @param <E> element type
 */
public interface ProxyChannel<E> {

    /**
     * @param impl the accept function for this consumer
     * @return a consumer instance to be used for this particular thread.
     */
    E proxyInstance(E impl);

    /**
     * @return a producer instance to be used for this particular thread.
     */
    E proxy();

    /**
     * @param impl into which the calls are made where they are not for a proxy instance
     * @param limit maximum number of calls to process through
     * @return the number of calls processed
     */
    int process(E impl, int limit);

    /**
     * @return the number of calls in the queue.
     */
    int size();

    /**
     * @return the maximum number of calls that can fit in this channel.
     */
    int capacity();

    boolean isEmpty();

}
