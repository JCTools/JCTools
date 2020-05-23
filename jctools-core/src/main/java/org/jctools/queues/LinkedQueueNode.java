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
import static org.jctools.util.UnsafeAccess.fieldOffset;

final class LinkedQueueNode<E>
{
    private final static long NEXT_OFFSET = fieldOffset(LinkedQueueNode.class,"next");

    private E value;
    private volatile LinkedQueueNode<E> next;

    LinkedQueueNode()
    {
        this(null);
    }

    LinkedQueueNode(E val)
    {
        spValue(val);
    }

    /**
     * Gets the current value and nulls out the reference to it from this node.
     *
     * @return value
     */
    public E getAndNullValue()
    {
        E temp = lpValue();
        spValue(null);
        return temp;
    }

    public E lpValue()
    {
        return value;
    }

    public void spValue(E newValue)
    {
        value = newValue;
    }

    public void soNext(LinkedQueueNode<E> n)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    public void spNext(LinkedQueueNode<E> n)
    {
        UNSAFE.putObject(this, NEXT_OFFSET, n);
    }

    public LinkedQueueNode<E> lvNext()
    {
        return next;
    }
}
