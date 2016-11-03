package org.jctools.channels.proxy;

public interface DemoIFace {
    
    public static class CustomType {
        
    }

    void call1(int x, int y);

    void call2(float x, double y, boolean z);

    void call3();

    void call4(Object x, CustomType y);

    void call5(CustomType x, int y, CustomType z);

    void call6(int x, CustomType[] y, CustomType... z);
}
