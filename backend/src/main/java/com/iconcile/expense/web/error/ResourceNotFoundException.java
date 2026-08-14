package com.iconcile.expense.web.error;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
