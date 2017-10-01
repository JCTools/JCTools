package org.jctools.util;

/**
 * JVM Information that is standard and available on all JVMs (i.e. does not use unsafe)
 */
@InternalAPI
public interface PortableJvmInfo {
    int CACHE_LINE_SIZE = Integer.getInteger("jctools.cacheLineSize", 64);
    int CPUs = Runtime.getRuntime().availableProcessors();
    int RECOMENDED_OFFER_BATCH = CPUs * 4;
    int RECOMENDED_POLL_BATCH = CPUs * 4;
}
