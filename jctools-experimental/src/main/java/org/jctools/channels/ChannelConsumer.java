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
 * The consumer is the object which controls the reading of messages from a
 * channel. Each consumer should only be assigned to a single thread.
 */
public interface ChannelConsumer {

    /**
     * Read a message from the channel.
     *
     * @return true if a message was read, false otherwise
     */
    boolean read();

    /**
     * Read as much message are available from the channel until limit.
     *
     * @param limit the maximum number of messages allowed to be read
     * @return the number of the messages read
     */
    default int read(int limit) {
        for (int i = 0; i < limit; i++) {
            if (!read()) {
                return i;
            }
        }
        return limit;
    }
}
