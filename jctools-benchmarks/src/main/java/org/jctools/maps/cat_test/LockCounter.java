package org.jctools.maps.cat_test;
import java.util.concurrent.locks.*;
public final class LockCounter extends Counter {
  public String name() { return "Lock"; }
  private final ReentrantLock _lock = new ReentrantLock();
  private long _cnt;
  public long get(){ return _cnt; }
  public void add( long x ) {
    try {
      _lock.lock();
      _cnt += x;
    } finally {
      _lock.unlock();
    }
  }
}
