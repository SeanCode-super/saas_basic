package com.saasbasics.platform.common.auth;

/** Issues credentials through the platform's password hashing implementation. */
public interface InitialPasswordIssuer {
    record Rules(int minLength, int maxLength, boolean requireUppercase, boolean requireLowercase,
                 boolean requireNumber, boolean requireSpecial) {}

    String generateInitialPassword(Rules rules);
    String hash(String password);
}
