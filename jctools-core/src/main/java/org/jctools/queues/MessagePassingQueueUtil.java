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

import org.jctools.util.InternalAPI;

@InternalAPI
public final class MessagePassingQueueUtil
{

    private MessagePassingQueueUtil()
    {

    }

    public static <E> int drain(
        MessagePassingQueue<? extends E> queue,
        MessagePassingQueue.Consumer<? super E> c,
        int limit)
    {
        E e;
        int i = 0;
        for (; i < limit && (e = queue.relaxedPoll()) != null; i++)
        {
            c.accept(e);
        }
        return i;
    }

    public static <E> int drain(MessagePassingQueue<? extends E> queue, MessagePassingQueue.Consumer<? super E> c)
    {
        E e;
        int i = 0;
        while ((e = queue.relaxedPoll()) != null)
        {
            i++;
            c.accept(e);
        }
        return i;
    }

    public static <E> void drain(
        MessagePassingQueue<? extends E> queue,
        MessagePassingQueue.Consumer<? super E> c,
        MessagePassingQueue.WaitStrategy wait,
        MessagePassingQueue.ExitCondition exit)
    {
        int idleCounter = 0;
        while (exit.keepRunning())
        {
            final E e = queue.relaxedPoll();
            if (e == null)
            {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

}
