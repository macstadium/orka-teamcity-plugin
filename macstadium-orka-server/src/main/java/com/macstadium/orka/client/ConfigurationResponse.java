package com.macstadium.orka.client;

public class ConfigurationResponse {
    private String message;

    private OrkaError[] errors;

    public String getMessage() {
        return this.message;
    }

    public OrkaError[] getErrors() {
        return this.errors.clone();
    }

    public boolean hasErrors() {
        return this.errors != null && this.errors.length > 0;
    }
}
