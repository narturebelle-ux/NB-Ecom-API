package com.narturebelle.exception;

/** Unknown product id. Maps to 404 PRODUCT_NOT_FOUND. */
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }
}
