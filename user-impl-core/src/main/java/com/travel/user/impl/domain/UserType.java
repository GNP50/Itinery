package com.travel.user.impl.domain;

/**
 * Distinguishes fully-registered users from anonymous (guest) sessions.
 * ADMIN users have additional privileges for administrative operations.
 */
public enum UserType {
    REGISTERED,
    ANONYMOUS,
    ADMIN
}
