package org.jctools.util;

/**
 * JVM Information that is standard and available on all JVMs (i.e. does not use unsafe)
 */
@InternalAPI
public interface PortableJvmInfo {
    int CACHE_LINE_SIZE = Integer.getInteger("jctools.cacheLineSize", 64);
    int CPUs = Runtime.getRuntime().availableProcessors();
}
