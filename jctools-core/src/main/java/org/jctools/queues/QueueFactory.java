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

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.jctools.util.CompilationResult;
import org.jctools.util.SimpleCompiler;
import org.jctools.util.Template;
import org.jctools.util.UnsafeAccess;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The queue factory produces {@link java.util.Queue} instances based on a best fit to the {@link ConcurrentQueueSpec}.
 * This allows minimal dependencies between user code and the queue implementations and gives users a way to express
 * their requirements on a higher level.
 * 
 * @author nitsanw
 * 
 */
public class QueueFactory {

    private static Map<Class, Class> blockingQueueCache = Collections.synchronizedMap(new HashMap<Class, Class>());

    public static <E> Queue<E> newQueue(ConcurrentQueueSpec qs) {
        if (qs.isBounded()) {
            // SPSC
            if (qs.isSpsc()) {
                return new SpscArrayQueue<E>(qs.capacity);
            }
            // MPSC
            else if (qs.isMpsc()) {
                if (qs.ordering != Ordering.NONE) {
                    return new MpscArrayQueue<E>(qs.capacity);
                } else {
                    return new MpscCompoundQueue<E>(qs.capacity);
                }
            }
            // SPMC
            else if (qs.isSpmc()) {
                return new SpmcArrayQueue<E>(qs.capacity);
            }
            // MPMC
            else {
                return new MpmcArrayQueue<E>(qs.capacity);
            }
        } else {
            // SPSC
            if (qs.isSpsc()) {
                return new SpscLinkedQueue<E>();
            }
            // MPSC
            else if (qs.isMpsc()) {
                if (UnsafeAccess.SUPPORTS_GET_AND_SET) {
                    return new MpscLinkedQueue8<E>();
                }
                else {
                    return new MpscLinkedQueue7<E>();
                }
            }
        }
        return new ConcurrentLinkedQueue<E>();
    }

    public static class BlockingModel
    {
        public String blockingQueueClassName;
        public String queueClassName;
        public String capacity;
    }

    public static <E> BlockingQueue<E> newBlockingQueue(ConcurrentQueueSpec qs)
    {
        if (!qs.isBounded())
        {
            throw new UnsupportedOperationException("Unbounded queues can't be blocking");
        }

        // SPSC
        if (qs.isSpsc()) {
            return getBlockingQueueFrom(SpscArrayQueue.class, qs.capacity);
        }
        // MPSC
        else if (qs.isMpsc()) {
            if (qs.ordering != Ordering.NONE) {
                return getBlockingQueueFrom(MpscArrayQueue.class, qs.capacity);
            } else {
                return getBlockingQueueFrom(MpscCompoundQueue.class, qs.capacity);
            }
        }
        // SPMC
        else if (qs.isSpmc()) {
            // Multiple consumers not supported yet
        }
        // MPMC
        else if (qs.isMpmc()) {
            // Multiple consumers not supported yet
        }

        return new ArrayBlockingQueue<E>(qs.capacity);
    }

    private static <E> BlockingQueue<E> getBlockingQueueFrom(Class<? extends Queue> queueClass, int capacity)
    {
        // Build model for template filling
        BlockingModel model = new BlockingModel();
        model.queueClassName = queueClass.getSimpleName();
        model.blockingQueueClassName = model.queueClassName + "Blocking";
        model.capacity = String.valueOf(capacity);

        // Check for the Queue in cache
        Class blockingClass = blockingQueueCache.get(queueClass);
        if (blockingClass==null)
        {
            // Load and fill template
            Template blockingTemplate = Template.fromFile(QueueFactory.class, "TemplateBlocking.java");
            String blockingQueueClassFile = blockingTemplate.render(model);

            // Compile result
            SimpleCompiler compiler = new SimpleCompiler();
            CompilationResult result = compiler.compile(model.blockingQueueClassName, blockingQueueClassFile);

            if (result.isSuccessful())
            {
                try
                {
                    // Load class
                    blockingClass = result.getClassLoader().loadClass(model.blockingQueueClassName);

                    // Store class in cache for later re-use
                    blockingQueueCache.put(queueClass, blockingClass);
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                System.out.println(result.getDiagnostics());
                return null;
            }
        }

        // Instantiate new Blocking queue
        BlockingQueue<E> q = null;
        try
        {
            q = (BlockingQueue<E>) blockingClass.newInstance();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        return q;
    }
}
