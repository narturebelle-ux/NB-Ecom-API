package com.narturebelle.dto;

import com.narturebelle.model.RecommendationPriority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Full replacement of a session's picks; an empty list clears them. */
public class SaveSessionRecommendationsRequest {

    @NotNull
    @Size(max = 30)
    @Valid
    private List<Item> items;

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        @NotNull private Long productId;
        @NotNull private RecommendationPriority priority;
        @Size(max = 500) private String note;

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public RecommendationPriority getPriority() { return priority; }
        public void setPriority(RecommendationPriority priority) { this.priority = priority; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
