package org.jctools.jmh.counters;

/**
 * Adapter interface to benchmark different kind of counters.
 *
 * @author Tolstopyatov Vsevolod
 */
interface Counter {

    void inc();

    long get();
}
