package org.jctools.jmh.counters;

/**
 * Adapter interface to benchmark different kind of counters. Using an abstract class rather to avoid class cast check
 * on JVMs which do not do CHA for interfaces (OpenJDK at current time)
 *
 * @author Tolstopyatov Vsevolod
 */
abstract class Counter {

    abstract void inc();

    abstract long get();
}
