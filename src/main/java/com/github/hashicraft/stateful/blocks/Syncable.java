package com.github.hashicraft.stateful.blocks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Syncable {
  public String key() default "";

  public String property() default "";

  public Class type() default Object.class;
}