package com.plantshop.auth.config;

import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Evaluates {@link ConditionalOnAuthMode} against {@code app.auth.mode}. */
class OnAuthModeCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Map<String, Object> attributes =
        metadata.getAnnotationAttributes(ConditionalOnAuthMode.class.getName());
    if (attributes == null || !(attributes.get("value") instanceof AuthMode required)) {
      return false;
    }
    Set<AuthMode> modes =
        Binder.get(context.getEnvironment())
            .bind("app.auth.mode", Bindable.setOf(AuthMode.class))
            .orElse(null);
    if (modes == null || modes.isEmpty()) {
      return required == AuthMode.GITHUB;
    }
    return modes.contains(required);
  }
}
