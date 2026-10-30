package com.narturebelle.exception;

/** Unknown checklist task id. Maps to 404 CHECKLIST_TASK_NOT_FOUND. */
public class ChecklistTaskNotFoundException extends RuntimeException {
    public ChecklistTaskNotFoundException(String message) {
        super(message);
    }
}
