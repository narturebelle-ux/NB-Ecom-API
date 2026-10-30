package com.narturebelle.exception;

/** Product health rule errors, mapped by status + code: 404 PRODUCT_HEALTH_RULE_NOT_FOUND,
 * 409 RULE_ALREADY_REVIEWED, 409 RULE_SELF_REVIEW_NOT_ALLOWED. */
public class ProductHealthRuleException extends RuntimeException {

    private final int status;
    private final String code;

    public ProductHealthRuleException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
