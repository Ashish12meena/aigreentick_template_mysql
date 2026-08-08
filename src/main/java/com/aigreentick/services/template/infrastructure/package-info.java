/**
 * Infrastructure layer — adapters that implement
 * {@code application.port.out} interfaces for real external systems
 * (Meta Graph API, WABA credential service, media upload service).
 *
 * Rule: adapters translate between the external system's shape and the
 * domain/application shape — they do not make business decisions. If
 * an adapter is branching on business rules, that logic has leaked out
 * of the application layer and should move back.
 *
 * This is also where Spring configuration/bean wiring
 * ({@code infrastructure.config}) lives.
 */
package com.aigreentick.services.template.infrastructure;
