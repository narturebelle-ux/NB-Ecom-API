package com.narturebelle.exception;

/** Thrown when POST /records is called but the object was never actually uploaded to storage. Maps to 409. */
public class RecordUploadNotConfirmedException extends RuntimeException {
    public RecordUploadNotConfirmedException(String message) {
        super(message);
    }
}
