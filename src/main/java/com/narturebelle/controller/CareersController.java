package com.narturebelle.controller;

import com.narturebelle.dto.JobApplicationForm;
import com.narturebelle.dto.JobResponse;
import com.narturebelle.model.JobApplication;
import com.narturebelle.service.CareersService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Public careers page -- no auth (permitted in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/careers/jobs")
public class CareersController {

    private final CareersService careersService;

    public CareersController(CareersService careersService) {
        this.careersService = careersService;
    }

    @GetMapping
    public ResponseEntity<List<JobResponse>> list() {
        return ResponseEntity.ok(careersService.openJobs().stream().map(JobResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(JobResponse.from(careersService.openJob(id)));
    }

    @PostMapping(value = "/{id}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> apply(@PathVariable Long id, @Valid @ModelAttribute JobApplicationForm form,
                                                     HttpServletRequest request) {
        if (form.getWebsite() != null && !form.getWebsite().isBlank()) {
            // Honeypot filled: a bot. Look successful, save nothing.
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", 0, "submittedAt", LocalDateTime.now()));
        }
        JobApplication saved = careersService.apply(id, form, request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", saved.getId(), "submittedAt", saved.getSubmittedAt()));
    }
}
