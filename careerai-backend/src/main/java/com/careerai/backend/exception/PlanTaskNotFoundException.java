package com.careerai.backend.exception;

/** The task key is not part of the user's plan for today. */
public class PlanTaskNotFoundException extends RuntimeException {

    public PlanTaskNotFoundException() {
        super("Task not found in today's plan");
    }
}
