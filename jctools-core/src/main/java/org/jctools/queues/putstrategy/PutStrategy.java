package org.jctools.queues.putstrategy;

public interface PutStrategy
{
    void backOff();
    void signal();
}
