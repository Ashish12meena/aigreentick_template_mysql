/**
 * Driven ports — what this service needs FROM the outside world:
 * Meta's Graph API (template + media), the WABA credential service,
 * and the audit sink. Implemented by adapters in
 * {@code infrastructure.client}.
 *
 * A use case must never depend on an {@code infrastructure} class
 * directly — only on one of these interfaces.
 */
package com.aigreentick.services.template.application.port.out;
