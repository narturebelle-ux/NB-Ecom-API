package com.narturebelle.exception;

/** Deleting a hospital/product that user data still points at (a referral, a dismissal). Maps to 409 CATALOG_ITEM_IN_USE. */
public class CatalogItemInUseException extends RuntimeException {
    public CatalogItemInUseException(String message) {
        super(message);
    }
}
