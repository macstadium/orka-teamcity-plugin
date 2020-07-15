package com.macstadium.orka.client;

public class DeletionResponse {
    private String message;

    private OrkaError[] errors;

    public DeletionResponse(String message, OrkaError[] errors) {
        this.message = message;
        this.errors = errors != null ? errors.clone() : new OrkaError[] {};
    }

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
