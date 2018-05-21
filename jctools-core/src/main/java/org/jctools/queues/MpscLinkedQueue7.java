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

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * @param <E>
 * @author nitsanw
 * @see MpscLinkedQueue
 */
public class MpscLinkedQueue7<E> extends MpscLinkedQueue<E>
{
    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal)
    {
        Object oldVal;
        do
        {
            oldVal = lvProducerNode();
        }
        while (!UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, oldVal, newVal));
        return (LinkedQueueNode<E>) oldVal;
    }
}
