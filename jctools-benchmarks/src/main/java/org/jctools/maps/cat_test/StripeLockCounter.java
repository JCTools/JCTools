package org.jctools.maps.cat_test;
import java.util.concurrent.locks.*;
public final class StripeLockCounter extends Counter {
  private final int _stripes;
  private final ReentrantLock[] _locks;
  private final long _cnts[];
  StripeLockCounter(int stripes) {
    _stripes = stripes;
    _locks = new ReentrantLock[stripes];
    _cnts = new long[stripes];
    for( int i=0; i<stripes; i++ )
      _locks[i] = new ReentrantLock();
  }
  public String name() { return "Locks"+_stripes; }
  public long get() {
    long sum = 0;
    for( int i=0; i<_cnts.length; i++ )
      sum += _cnts[i];
    return sum;
  }
  public void add( long x ) {
    int hash = System.identityHashCode( Thread.currentThread());
    int idx = hash & (_locks.length-1);
    final Lock l = _locks[idx];
    try {
      l.lock();
      _cnts [idx] += x;
    } finally {
      l.unlock();
    }
  }
}
