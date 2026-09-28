package com.apargo.platform.contract.audit;

import com.apargo.platform.contract.identity.ActorType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who performed the action.
 *
 * @param type           required
 * @param id             required: the user id for {@code USER}, the calling
 *                       service's name for {@code SERVICE}, the job name for
 *                       {@code SYSTEM}
 * @param name           display name at the time, or {@code null} when the
 *                       service does not have it
 * @param impersonatorId the real user when someone acts as another user;
 *                       {@code null} until the gateway provides it
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditActorDto(ActorType type, String id, String name, String impersonatorId) {

    public static AuditActorDto user(String userId) {
        return new AuditActorDto(ActorType.USER, userId, null, null);
    }

    public static AuditActorDto service(String serviceName) {
        return new AuditActorDto(ActorType.SERVICE, serviceName, null, null);
    }

    public static AuditActorDto system(String jobName) {
        return new AuditActorDto(ActorType.SYSTEM, jobName, null, null);
    }
}
