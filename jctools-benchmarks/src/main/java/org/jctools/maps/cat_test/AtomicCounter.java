package org.jctools.maps.cat_test;
import java.util.concurrent.atomic.*;
public final class AtomicCounter extends Counter {
  public String name() { return "Atomic"; }
  private final AtomicLong _cnt = new AtomicLong();
  public long get(){ return _cnt.get(); }
  public void add( long x ) { _cnt.getAndAdd(x); }
}
