/**
 * Driving ports — one interface per business operation this service
 * exposes. Controllers (or any future entry point: a scheduled job, a
 * message listener) depend on these interfaces, never on the concrete
 * {@code *UseCaseImpl} class in {@code application.usecase}.
 */
package com.aigreentick.services.template.application.port.in;
