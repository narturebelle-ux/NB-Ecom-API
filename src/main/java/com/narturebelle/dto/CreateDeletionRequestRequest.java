package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** The literal word DELETE, typed by the user, as the final confirmation step. */
public class CreateDeletionRequestRequest {

    @NotNull
    @Pattern(regexp = "DELETE", message = "must be exactly DELETE")
    private String confirm;

    public String getConfirm() { return confirm; }
    public void setConfirm(String confirm) { this.confirm = confirm; }
}
