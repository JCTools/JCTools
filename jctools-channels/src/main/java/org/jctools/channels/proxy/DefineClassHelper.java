package org.jctools.channels.proxy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import org.jctools.util.UnsafeAccess;

import sun.misc.Unsafe;

final class DefineClassHelper {
    private static abstract class DefineClassStrategy {
        abstract Class<?> defineClass(
                Class<?> iFace,
                String name,
                byte[] byteCode);
    }

    private static final class UsingUnsafe extends DefineClassStrategy {
        private final Method defineClassMethod;

        private UsingUnsafe(Method defineClassMethod) {
            this.defineClassMethod = defineClassMethod;
        }

        @Override
        Class<?> defineClass(
                Class<?> iFace,
                String name,
                byte[] byteCode) {
            try {
                // UnsafeAccess.UNSAFE.defineClass(name, byteCode, 0, byteCode.length, iFace.getClassLoader(), null);
                return (Class<?>) defineClassMethod.invoke(
                        UnsafeAccess.UNSAFE,
                        name,
                        byteCode,
                        0,
                        byteCode.length,
                        iFace.getClassLoader(),
                        null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    private static final class UsingMethodHandles extends DefineClassStrategy {

        private final Method defineClassMethod;
        private final Method lookupMethod;
        private final Method privateLookupInMethod;

        private UsingMethodHandles(Method lookupMethod, Method privateLookupInMethod, Method defineClassMethod) {
            this.defineClassMethod = defineClassMethod;
            this.lookupMethod = lookupMethod;
            this.privateLookupInMethod = privateLookupInMethod;
        }

        @Override
        Class<?> defineClass(
                Class<?> iFace,
                String name,
                byte[] byteCode) {
            try {
                // Lookup lookup = MethodHandles.lookup();
                Object lookup = lookupMethod.invoke(null);
                // Lookup privateLookupIn = MethodHandles.privateLookupIn(iFace, lookup);
                Object privateLookupIn = privateLookupInMethod.invoke(null, iFace, lookup);
                // return privateLookupIn.defineClass(byteCode);
                return (Class<?>) defineClassMethod.invoke(privateLookupIn, byteCode);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
        }

    }

    private static final DefineClassStrategy DEFINE_CLASS_STRATEGY = initialize();

    private static DefineClassStrategy initialize() {
        try {
            try {
                return usingUnsafe();
            } catch (Exception ignored) {
                return usingMethodHandles();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize suitable defineClass implementation", e);
        }
    }

    private static DefineClassStrategy usingUnsafe() throws Exception {
        Method defineClassMethod = Unsafe.class.getMethod("defineClass",
                String.class,
                byte[].class,
                int.class,
                int.class,
                ClassLoader.class,
                ProtectionDomain.class);
        return new UsingUnsafe(defineClassMethod);
    }

    /**
     * Should be available from JDK 1.9 and later
     * @return a strategy for calling defineClass
     * @throws Exception if anything goes wrong
     */
    private static DefineClassStrategy usingMethodHandles() throws Exception {
        Class<?> lookupClass = Class.forName("java.lang.invoke.MethodHandles$Lookup", false, null);
        Class<?> methodHandlesClass = Class.forName("java.lang.invoke.MethodHandles", false, null);

        Method lookupMethod = methodHandlesClass.getMethod("lookup");
        Method privateLookupInMethod = methodHandlesClass.getMethod("privateLookupIn", Class.class, lookupClass);
        Method defineClassMethod = lookupClass.getMethod("defineClass", byte[].class);

        return new UsingMethodHandles(lookupMethod, privateLookupInMethod, defineClassMethod);
    }

    static Class<?> defineClass(Class<?> iFace, String name, byte[] bytes) {
        return DEFINE_CLASS_STRATEGY.defineClass(iFace, name, bytes);
    }
}
