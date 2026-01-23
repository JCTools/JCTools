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
package org.jctools.queues.varhandle.utils;

import org.jctools.queues.*;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.varhandle.*;
import org.jctools.queues.varhandle.unpadded.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The queue factory produces {@link java.util.Queue} instances based on a best fit to the {@link ConcurrentQueueSpec}.
 * This allows minimal dependencies between user code and the queue implementations and gives users a way to express
 * their requirements on a higher level.
 */
public class VarHandleQueueFactory
{
    public static <E> Queue<E> newVarHandleQueue(ConcurrentQueueSpec qs)
    {
        if (qs.isBounded())
        {
            // SPSC
            if (qs.isSpsc())
            {
                return new SpscVarHandleArrayQueue<E>(qs.capacity);
            }
            // MPSC
            else if (qs.isMpsc())
            {
                return new MpscVarHandleArrayQueue<E>(qs.capacity);
            }
            // SPMC
            else if (qs.isSpmc())
            {
                return new SpmcVarHandleArrayQueue<E>(qs.capacity);
            }
            // MPMC
            else
            {
                return new MpmcVarHandleArrayQueue<E>(qs.capacity);
            }
        }
        else
        {
            // SPSC
            if (qs.isSpsc())
            {
                return new SpscLinkedVarHandleQueue<E>();
            }
            // MPSC
            else if (qs.isMpsc())
            {
                return new MpscLinkedVarHandleQueue();
            }
        }
        return new ConcurrentLinkedQueue<E>();
    }

    public static <E> Queue<E> newVarHandleUnpaddedQueue(ConcurrentQueueSpec qs)
    {
        if (qs.isBounded())
        {
            // SPSC
            if (qs.isSpsc())
            {
                return new SpscVarHandleUnpaddedArrayQueue<E>(qs.capacity);
            }
            // MPSC
            else if (qs.isMpsc())
            {
                return new MpscVarHandleUnpaddedArrayQueue<E>(qs.capacity);
            }
            // SPMC
            else if (qs.isSpmc())
            {
                return new SpmcVarHandleUnpaddedArrayQueue<E>(qs.capacity);
            }
            // MPMC
            else
            {
                return new MpmcVarHandleUnpaddedArrayQueue<E>(qs.capacity);
            }
        }
        else
        {
            // SPSC
            if (qs.isSpsc())
            {
                return new SpscLinkedVarHandleUnpaddedQueue<E>();
            }
            // MPSC
            else if (qs.isMpsc())
            {
                return new MpscLinkedVarHandleUnpaddedQueue();
            }
        }
        return new ConcurrentLinkedQueue<E>();
    }
}
