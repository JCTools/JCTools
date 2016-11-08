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
package org.jctools.queues.intrusive;

/**
 * Intrusive queue nodes are required to implement this interface.
 *
 * @see NodeImpl for a base implementation
 */
public interface Node {
    /**
     * Stores a pointer to the next node in the linked queue structure. This corresponds to
     * mpscq_node_t.next in the <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/intrusive-mpsc-node-based-queue">
     * 1024cores post Intrusive MPSC node-based queue</a>. Note the volatile semantics of the stores in the algorithm.
     */
    void setNext(Node next);

    Node getNext();
}
