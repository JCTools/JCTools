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

final class IndexedQueueSizeUtil {
    private IndexedQueueSizeUtil(){}
    protected interface IndexedQueue {
        long lvConsumerIndex();
        long lvProducerIndex();
    }
    static int size(IndexedQueue iq) {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = iq.lvConsumerIndex();
        long size;
        while (true) {
            final long before = after;
            final long currentProducerIndex = iq.lvProducerIndex();
            after = iq.lvConsumerIndex();
            if (before == after) {
                size = (currentProducerIndex - after);
                break;
            }
        }
        // Long overflow is impossible (), so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        else {
            return (int) size;
        }
    }

    static boolean isEmpty(IndexedQueue iq) {
        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (iq.lvConsumerIndex() == iq.lvProducerIndex());
    }
}
