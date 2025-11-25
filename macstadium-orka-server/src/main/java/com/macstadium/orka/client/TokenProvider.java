package com.macstadium.orka.client;

import java.io.IOException;

/**
 * Interface for providing authentication tokens for Orka API.
 * Implementations can provide static tokens or dynamically refresh tokens (e.g., AWS EKS tokens).
 */
public interface TokenProvider {
    /**
     * Returns a valid token for Orka API authentication.
     * Implementations may cache tokens and refresh them when expired.
     *
     * @return Bearer token string
     * @throws IOException if token cannot be obtained
     */
    String getToken() throws IOException;

    /**
     * Validates the provider configuration.
     *
     * @return true if the provider is properly configured and can potentially provide tokens
     */
    boolean isValid();
}

