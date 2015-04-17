package org.jctools.util;

public interface JvmInfo {
    public static final int CACHE_LINE_SIZE = Integer.getInteger("jctools.cacheLineSize", 64);
    public static final int PAGE_SIZE = UnsafeAccess.UNSAFE.pageSize();
}
