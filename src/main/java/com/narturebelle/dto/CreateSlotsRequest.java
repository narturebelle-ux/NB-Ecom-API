package com.narturebelle.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import java.time.LocalDateTime;
import java.util.List;

public class CreateSlotsRequest {

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

        public LocalDateTime getStartAt() { return startAt; }
        public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

        public LocalDateTime getEndAt() { return endAt; }
        public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    }
}
