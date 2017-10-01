package org.jctools.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation marks classes and methods which may be public for any reason (to support better testing or reduce
 * code duplication) but are not intended as public API and may change between releases without the change being
 * considered a breaking API change (a major release).
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.SOURCE)
public @interface InternalAPI
{
}
