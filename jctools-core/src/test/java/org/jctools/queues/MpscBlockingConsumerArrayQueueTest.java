/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jctools.queues;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

public class MpscBlockingConsumerArrayQueueTest {

    @Test
    public void testPollTimeout() throws InterruptedException {

        final MpscBlockingConsumerArrayQueue<Object> queue =
            new MpscBlockingConsumerArrayQueue<>(128000);

        final Thread consumerThread = new Thread(() -> {
            try {
                while (true) {
                    queue.poll(100, TimeUnit.NANOSECONDS);
                }
            }
            catch (InterruptedException e) {
            }
        });

        consumerThread.start();

        final Thread producerThread = new Thread(() -> {
            while (!Thread.interrupted()) {
                for (int i = 0; i < 10; ++i) {
                    queue.offer("x");
                }
                LockSupport.parkNanos(100000);
            }
        });

        producerThread.start();

        Thread.sleep(10000);

        consumerThread.interrupt();
        consumerThread.join();

        producerThread.interrupt();
        producerThread.join();
    }
}
