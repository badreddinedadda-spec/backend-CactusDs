package com.cactusds.backend.comon.monitoring;

public class AgentUnreachableException extends RuntimeException {
    public AgentUnreachableException(String message) {
        super(message);
    }
}
