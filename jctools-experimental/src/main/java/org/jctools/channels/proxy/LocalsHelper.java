package org.jctools.channels.proxy;

import static java.lang.System.out;

import org.objectweb.asm.Type;

public final class LocalsHelper {
    private int nextLocalIndex = 0;

    private LocalsHelper() {
    };

    public int newLocal(Class<?> cls) {
        Type type = Type.getType(cls);
        return newLocal(type);
    }
    

    private int newLocal(Type type) {
        final int myIndex = nextLocalIndex;
        nextLocalIndex += type.getSize();
        return myIndex;
    }

    public static LocalsHelper forStaticMethod() {
        return new LocalsHelper();
    }

    public static LocalsHelper forInstanceMethod() {
        LocalsHelper helper = new LocalsHelper();
        helper.newLocal(Object.class);
        return helper;
    }

    public static void main(String[] args) throws Exception {
        LocalsHelper helper =
                LocalsHelper.forInstanceMethod();
        out.println(helper.newLocal(int.class));
        out.println(helper.newLocal(int.class));
        out.println(helper.newLocal(long.class));
        out.println(helper.newLocal(double.class));
        out.println(helper.newLocal(boolean.class));
        out.println(helper.newLocal(Object.class));
        out.println(helper.newLocal(Object.class));
        out.println(helper.newLocal(Object.class));
        out.println();
    }
}
