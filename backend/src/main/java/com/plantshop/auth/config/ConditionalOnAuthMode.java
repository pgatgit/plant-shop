package com.plantshop.auth.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/** Registers the annotated bean only when {@code app.auth.mode} contains the given mode. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnAuthModeCondition.class)
public @interface ConditionalOnAuthMode {

  /**
   * The required mode.
   *
   * @return the mode
   */
  AuthMode value();
}
