package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;

/** Deactivating a coordinator hides her open slots from users; booked calls are untouched. */
public class UpdateCoordinatorRequest {

    @NotNull
    private Boolean active;

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
