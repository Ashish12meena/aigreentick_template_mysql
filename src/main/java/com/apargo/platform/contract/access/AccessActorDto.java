package com.apargo.platform.contract.access;

import com.apargo.platform.contract.identity.ActorType;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Who attempted access.
 *
 * @param type required
 * @param id   the resolved user or service id; {@code null} when a sign-in
 *             failed before the principal was identified
 * @param name display name, when known
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessActorDto(ActorType type, String id, String name) {
}
