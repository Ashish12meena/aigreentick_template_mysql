package com.aigreentick.services.template.common.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a create/send endpoint that honours {@code X-Idempotency-Key}
 * (API Standard §1).
 *
 * <p>The first request with a key runs normally and its 2xx response is
 * stored. A repeat with the same key and the same request replays that
 * response instead of running again — a replayed {@code 201} is returned as
 * {@code 200} ("already existed"). The same key with a different request, or
 * while the first is still running, is a {@code 409}.
 *
 * <p>Implemented by the interceptor in {@code infrastructure.idempotency};
 * this annotation lives in {@code common} so controllers can use it without
 * importing infrastructure.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
