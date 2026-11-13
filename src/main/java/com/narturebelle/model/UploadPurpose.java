package com.narturebelle.model;

/**
 * What an upload outside the records vault is for. Each purpose has its own rules and its own save call,
 * and an upload started for one purpose can never be saved under another.
 */
public enum UploadPurpose {
    /** A file answer on a care professional's application (questionKey). */
    PROVIDER_APPLICATION,
    /** A supporting document for a live professional's profile change request. */
    PROVIDER_CHANGE_REQUEST,
    /** A résumé on the public careers page. No signed-in user; tied to the job by an upload token. */
    CAREERS_RESUME
}
