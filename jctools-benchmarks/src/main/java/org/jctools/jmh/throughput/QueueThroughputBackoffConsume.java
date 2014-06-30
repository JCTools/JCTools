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
package org.jctools.jmh.throughput;

import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.infra.Blackhole;


public class QueueThroughputBackoffConsume extends QueueThroughputBackoffNone {
    
    @Param({"1","10","100","1000"})
    private long consumeAmount;
    @Override
    protected void backoff() {
        Blackhole.consumeCPU(consumeAmount);
    }
}
