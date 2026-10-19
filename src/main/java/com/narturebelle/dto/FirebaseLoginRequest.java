package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import java.time.LocalDate;

public class FirebaseLoginRequest {
    private String idToken;
    private PregnancyStage stage;
    private LocalDate dueDate;

    public FirebaseLoginRequest() {}

    public String getIdToken() { return idToken; }
    public void setIdToken(String idToken) { this.idToken = idToken; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
