package org.jctools.util;

@InternalAPI
public interface UnsafeJvmInfo {
    int PAGE_SIZE = UnsafeAccess.UNSAFE.pageSize();
}
