package com.narturebelle.dto;

import com.narturebelle.model.RecordCategory;
import jakarta.validation.constraints.NotNull;

public class PatchRecordRequest {

    @NotNull
    private RecordCategory category;

    public RecordCategory getCategory() { return category; }
    public void setCategory(RecordCategory category) { this.category = category; }
}
