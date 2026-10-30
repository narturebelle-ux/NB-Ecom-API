package com.narturebelle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class CreateOnboardingCallSlotsRequest {

    @NotEmpty
    @Valid
    private List<SlotWindow> slots;

    public List<SlotWindow> getSlots() { return slots; }
    public void setSlots(List<SlotWindow> slots) { this.slots = slots; }

    public static class SlotWindow {
        @NotNull
        private LocalDateTime startAt;

        @NotNull
        private LocalDateTime endAt;

        /** The active coordinator who'll take this call. */
        @NotNull
        private Long coordinatorId;

        public Long getCoordinatorId() { return coordinatorId; }
        public void setCoordinatorId(Long coordinatorId) { this.coordinatorId = coordinatorId; }

        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }
}
