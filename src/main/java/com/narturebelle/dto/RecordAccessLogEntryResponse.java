package com.narturebelle.dto;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordAccessAction;
import com.narturebelle.model.RecordAccessEvent;
import com.narturebelle.model.RecordAccessRelationship;

import java.time.LocalDateTime;

/** recordName is joined in at read-time from the HealthRecord already loaded for the access check — never stored on RecordAccessEvent itself. */
public class RecordAccessLogEntryResponse {

    private Long id;
    private Long recordId;
    private String recordName;
    private RecordAccessAction action;
    private RecordAccessRelationship actorRelationship;
    private LocalDateTime occurredAt;

    public static RecordAccessLogEntryResponse from(RecordAccessEvent event, HealthRecord record) {
        RecordAccessLogEntryResponse dto = new RecordAccessLogEntryResponse();
        dto.id = event.getId();
        dto.recordId = event.getRecordId();
        dto.recordName = record.getOriginalFileName();
        dto.action = event.getAction();
        dto.actorRelationship = event.getActorRelationship();
        dto.occurredAt = event.getOccurredAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public String getRecordName() { return recordName; }
    public RecordAccessAction getAction() { return action; }
    public RecordAccessRelationship getActorRelationship() { return actorRelationship; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}
