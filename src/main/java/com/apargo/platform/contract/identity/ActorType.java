package com.apargo.platform.contract.identity;

/**
 * Kind of principal behind an action: a person, another service calling
 * without a user, or a scheduled job of the platform itself.
 */
public enum ActorType {
    USER,
    SERVICE,
    SYSTEM
}
