package org.jctools.maps.cat_test;
public final class SyncCounter extends Counter {
  public String name() { return "Synchronized"; }
  private long _cnt;
  public long get(){ return _cnt; }
  public synchronized void add( long x ) { _cnt += x; }
}
