package com.narturebelle.controller;

import com.narturebelle.dto.HealthRecordResponse;
import com.narturebelle.dto.MultipartUploadRequests;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.service.RecordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Support restores a video from trash when the mother asks (F9-S19 item 13, D49). Works while the video
 * is still in its 7-day trash period. /api/v1/admin/** is already role-gated by SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/records")
public class AdminRecordController {

    private final RecordService recordService;

    public AdminRecordController(RecordService recordService) {
        this.recordService = recordService;
    }

    @PostMapping("/{id}/restore-video")
    public ResponseEntity<HealthRecordResponse> restoreVideo(@PathVariable Long id,
                                                             @Valid @RequestBody MultipartUploadRequests.Restore req) {
        HealthRecord record = recordService.find(id).orElse(null);
        if (record == null) {
            return ResponseEntity.notFound().build();
        }
        HealthRecord saved = recordService.restoreVideo(record, req.getReason());
        return ResponseEntity.ok(HealthRecordResponse.from(saved, recordService.stateOf(saved)));
    }
}
