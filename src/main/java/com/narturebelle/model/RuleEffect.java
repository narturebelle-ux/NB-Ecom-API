package com.narturebelle.model;

/** AVOID hides products carrying the tag; PREFER ranks them first and adds the rule's reason. AVOID wins if both apply. */
public enum RuleEffect {
    AVOID,
    PREFER
}
