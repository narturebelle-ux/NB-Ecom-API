package com.narturebelle.exception;

import org.springframework.http.HttpStatus;

/**
 * File-state errors for the records vault (F9-S19), with a code the UI can switch on:
 * FILE_NOT_READY (409, scan not finished), FILE_BLOCKED (409, infected or scan failed),
 * FILE_IN_TRASH (409, video past its removal date), VIDEO_GONE (409, nothing left to restore),
 * UPLOAD_NOT_FOUND (404), STORAGE_QUOTA_EXCEEDED (409).
 */
public class RecordFileException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public RecordFileException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static RecordFileException notReady() {
        return new RecordFileException(HttpStatus.CONFLICT, "FILE_NOT_READY",
                "This file is still being checked for safety. Please try again in a minute.");
    }

    public static RecordFileException uploadNotFound() {
        return new RecordFileException(HttpStatus.NOT_FOUND, "UPLOAD_NOT_FOUND",
                "No upload in progress matches this request. Please start the upload again.");
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
