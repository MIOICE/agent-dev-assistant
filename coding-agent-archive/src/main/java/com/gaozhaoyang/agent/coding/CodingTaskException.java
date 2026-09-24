package com.gaozhaoyang.agent.coding;

public class CodingTaskException extends RuntimeException {
    public CodingTaskException(String message) {
        super(message);
    }

    public CodingTaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
