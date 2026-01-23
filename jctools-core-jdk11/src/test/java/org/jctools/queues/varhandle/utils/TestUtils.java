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

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.queues.spec.Preference;

import static org.jctools.queues.varhandle.utils.VarHandleQueueFactory.newVarHandleQueue;
import static org.jctools.queues.varhandle.utils.VarHandleQueueFactory.newVarHandleUnpaddedQueue;

public class TestUtils {

    public static Object[] makeVarHandle(int producers, int consumers, int capacity, Ordering ordering)
    {
        ConcurrentQueueSpec spec = makeSpec(producers, consumers, capacity, ordering);
        return new Object[] {spec, newVarHandleQueue(spec)};
    }

    public static Object[] makeVarHandleUnpadded(int producers, int consumers, int capacity, Ordering ordering)
    {
        ConcurrentQueueSpec spec = makeSpec(producers, consumers, capacity, ordering);
        return new Object[] {spec, newVarHandleUnpaddedQueue(spec)};
    }

    public static ConcurrentQueueSpec makeSpec(int producers, int consumers, int capacity, Ordering ordering)
    {
        return new ConcurrentQueueSpec(producers, consumers, capacity, ordering, Preference.NONE);
    }
}
