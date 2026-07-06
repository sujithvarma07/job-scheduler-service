package com.sujith.scheduler.exception;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID id) {
        super("job not found: " + id);
    }

    public JobNotFoundException(String message) {
        super(message);
    }
}
