package org.jctools.maps.cat_test;

import org.jctools.util.UnsafeAccess;

import static org.jctools.util.UnsafeAccess.fieldOffset;

public final class UnsafeCounter extends Counter {
  public String name() { return "Unsafe"; }

  private static final long CNT_OFFSET = fieldOffset(UnsafeCounter.class, "_cnt");

  private long _cnt;
  public long get(){ return _cnt; }
  public void add( final long x ) { 
    long cnt=0;
    do { 
      cnt = _cnt;
    } while( !UnsafeAccess.UNSAFE.compareAndSwapLong(this,CNT_OFFSET,cnt,cnt+x) );
  }
}
