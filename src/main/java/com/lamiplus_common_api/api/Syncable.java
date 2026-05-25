package com.lamiplus_common_api.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Syncable {
    int priority() default 1;
    String uuidColumn() default "uuid";
    boolean enabled() default true;
}