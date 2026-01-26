package com.macstadium.orka.client;

import com.intellij.openapi.util.text.StringUtil;

/**
 * Token provider that returns a static, pre-configured token.
 * This is the default behavior for manual token configuration.
 */
public class StaticTokenProvider implements TokenProvider {
    private final String token;

    public StaticTokenProvider(String token) {
        this.token = token;
    }

    @Override
    public String getToken() {
        return this.token;
    }

    @Override
    public boolean isValid() {
        return StringUtil.isNotEmpty(this.token);
    }

    @Override
    public void invalidateToken() {
        // Static token cannot be refreshed - no-op
    }
}

