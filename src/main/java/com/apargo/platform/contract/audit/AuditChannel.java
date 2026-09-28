package com.apargo.platform.contract.audit;

/**
 * How the action reached the source service.
 *
 * <ul>
 *   <li>{@link #WEB} - a request carrying {@code X-User-Id}.</li>
 *   <li>{@link #API} - an internal service-to-service call with no user.</li>
 *   <li>{@link #MOBILE} - reserved until the gateway reports the client type.</li>
 *   <li>{@link #WORKER} - background work, whether started by a user or by a scheduler.</li>
 * </ul>
 */
public enum AuditChannel {
    WEB,
    API,
    MOBILE,
    WORKER
}
