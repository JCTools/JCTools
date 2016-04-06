package org.cliffc.high_scale_lib.cat_test;
import org.cliffc.high_scale_lib.ConcurrentAutoTable;

public final class CATCounter extends Counter {
  public String name() { return "CAT"; }
  private final ConcurrentAutoTable _tab = new ConcurrentAutoTable();
  public long get(){ return _tab.get(); }
  public void add( long x ) {  _tab.add(x); }
  public void print() { _tab.print(); }
  public int internal_size() { return _tab.internal_size(); }
}
