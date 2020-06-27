package org.jctools.uc;

import org.jctools.stacks.WFIStack;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * FlatCombining is a <em>universal construct</em> which can provide thread-safe and concurrent access to an
 * <em>arbitrary</em> sequential data structure.
 * <p>
 * The only requirement is that all accesses to the data structure must be delivered as
 * {@linkplain Operation operations} through the {@link #apply(Operation)} method.
 * <p>
 * Each operation will only execute exactly once, and there will only be a single copy of the underlying data structure.
 * <p>
 * The only consequcne is that the {@link #apply(Operation)} method may block.
 * <p>
 * This is conceptually similar to wrapping the data structure in a mutex, but the {@code FlatCombining} implementation
 * will scale positively with the level of concurrency, while contended locks typically scales negatively.
 * Negative scalability is when the system-wide throughput drops as the number of active threads increases.
 * <p>
 * The Flat Combining concept is described by the paper
 * <a href="https://www.cs.bgu.ac.il/~hendlerd/papers/flat-combining.pdf">
 * Flat Combining and the Synchronization-Parallelism Tradeoff</a>
 *
 * @param <T> The type of data managed by this flat combining instance.
 */
public class FlatCombining<T> {
    private final WFIStack<OpNode> ops;
    private final AtomicBoolean lock;
    private final T data;

    public FlatCombining(T sequentialData) {
        ops = new WFIStack<OpNode>();
        lock = new AtomicBoolean();
        data = sequentialData;
    }

    /**
     * Apply the given operation on the underlying data structure. This call may block. Multiple threads can calls
     * this at the same time. The operations will be applied with linearizable consistency.
     * <p>
     * The operation will only be applied once to the data structure, but there is no guarantee that it will be
     * applied by the calling thread.
     *
     * @param operation The operation to apply to the underlying data structure.
     * @param <R>       The type of return value produced by the operation, if any.
     * @param <E>       The type of exception the operation might throw, if any.
     * @return The result returned by the operation.
     * @throws E                    If the operation throws an exception.
     * @throws InterruptedException If the apply method ends up blocking but the thread is interrupted.
     */
    public <R, E extends Exception> R apply(Operation<T, R, E> operation) throws E, InterruptedException {
        OpNode op = enqueue(operation);

        for (; ; ) {
            if (tryLock()) {
                Iterable<OpNode> nodes = combineAndApplyAllOperations();
                unlockAndUnparkWaiters(nodes);
            } else {
                op.await();
            }
            if (op.isDone()) {
                try {
                    return op.getResult();
                } catch (InterruptedException ie) {
                    throw ie;
                } catch (Exception e) {
                    //noinspection unchecked
                    throw (E) e;
                } catch (Throwable th) {
                    throw new RuntimeException(th);
                }
            }
        }
    }

    private <R, E extends Exception> OpNode enqueue(Operation<T, R, E> operation) {
        if (operation == null) {
            throw new NullPointerException("The operation cannot be null.");
        }
        OpNode op = new OpNode(operation);
        ops.push(op);
        return op;
    }

    private boolean tryLock() {
        return !lock.get() && lock.compareAndSet(false, true);
    }

    private Iterable<OpNode> combineAndApplyAllOperations() {
        Iterable<OpNode> nodes = ops.popAllFifo();
        for (OpNode curr : nodes) {
            curr.apply(data);
        }
        return nodes;
    }

    private void unlockAndUnparkWaiters(Iterable<OpNode> nodes) {
        lock.set(false);
        OpNode peek = ops.peek();
        if (peek != null) {
            peek.unpark();
        }
        for (OpNode node : nodes) {
            node.unpark();
        }
    }

    /**
     * An operation that can be applied to the given data structure via the {@link #apply(Operation)} method.
     *
     * @param <T> The type of data structure this operation can be applied to.
     * @param <R> The type of data returned by this operation.
     * @param <E> The type of exception this operation might throw.
     */
    public interface Operation<T, R, E extends Exception> {
        /**
         * Apply this operation to the (likely mutable) data structure given as a parameter.
         * <p>
         * The code can assume that is has exclusive access to the data when this method is called.
         *
         * @param obj The data to apply this operation to.
         * @return The potential return value of this operation.
         * @throws E An exception, if any, that this operation might throw.
         */
        R apply(T obj) throws E;
    }

    private static class OpNode extends WFIStack.Node {
        private final Operation<Object, Object, Exception> operation;
        private final Thread requestingThread;
        private volatile Throwable exceptionResult;
        private volatile Object result;
        private volatile boolean done;

        private OpNode(Operation<?, ?, ?> operation) {
            //noinspection unchecked
            this.operation = (Operation<Object, Object, Exception>) operation;
            this.requestingThread = Thread.currentThread();
        }

        public void apply(Object data) {
            try {
                result = operation.apply(data);
            } catch (Throwable e) {
                exceptionResult = e;
            } finally {
                done = true;
            }
        }

        public <R> R getResult() throws Throwable {
            while (!done) {
                await();
            }
            Throwable er = this.exceptionResult;
            if (er != null) {
                throw er;
            }
            //noinspection unchecked
            return (R) result;
        }

        public void unpark() {
            LockSupport.unpark(requestingThread);
        }

        public void await() throws InterruptedException {
            LockSupport.park(operation);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }

        public boolean isDone() {
            return done;
        }
    }
}
