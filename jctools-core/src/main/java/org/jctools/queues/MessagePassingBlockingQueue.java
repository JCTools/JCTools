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
package org.jctools.queues;

import java.util.concurrent.BlockingQueue;

/**
 * A {@link MessagePassingQueue} that also implements the {@link BlockingQueue}
 * contract, combining high-performance message-passing semantics with
 * blocking and timed waiting capabilities.
 *
 * @param <E> the type of elements held in this queue
 * @see MessagePassingQueue
 * @see BlockingQueue
 */
public interface MessagePassingBlockingQueue<E> extends MessagePassingQueue<E>, BlockingQueue<E> {
}
