package com.github.codert96.mybatis.annotation;

import java.lang.annotation.*;

@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface Meta {

    String EL_USERNAME = "#authentication?.username";

    String EL_NOW = "#now";

    String value() default "";

    /**
     * 执行 spring el 表达式
     */
    String el() default "";
}
