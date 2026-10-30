package com.narturebelle.controller;

import com.narturebelle.dto.JobApplicationResponse;
import com.narturebelle.dto.JobRequest;
import com.narturebelle.dto.JobResponse;
import com.narturebelle.model.JobPosting;
import com.narturebelle.service.CareersService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/careers")
public class AdminCareersController {

    private final CareersService careersService;

    public AdminCareersController(CareersService careersService) {
        this.careersService = careersService;
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<JobResponse>> list() {
        Map<Long, Long> counts = careersService.applicationCounts();
        return ResponseEntity.ok(careersService.allJobs().stream()
                .map(job -> JobResponse.forAdmin(job, counts.getOrDefault(job.getId(), 0L)))
                .toList());
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest req) {
        return ResponseEntity.ok(withCount(careersService.create(req)));
    }

    @PutMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> replace(@PathVariable Long id, @Valid @RequestBody JobRequest req) {
        return ResponseEntity.ok(withCount(careersService.replace(id, req)));
    }

    @PostMapping("/jobs/{id}/close")
    public ResponseEntity<JobResponse> close(@PathVariable Long id) {
        return ResponseEntity.ok(withCount(careersService.setOpen(id, false)));
    }

    @PostMapping("/jobs/{id}/reopen")
    public ResponseEntity<JobResponse> reopen(@PathVariable Long id) {
        return ResponseEntity.ok(withCount(careersService.setOpen(id, true)));
    }

    @GetMapping("/jobs/{id}/applications")
    public ResponseEntity<List<JobApplicationResponse>> applications(@PathVariable Long id) {
        return ResponseEntity.ok(careersService.applications(id).stream().map(JobApplicationResponse::from).toList());
    }

    /** The résumé file itself, as an attachment (the UI fetches it with the admin token and saves the blob). */
    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<byte[]> resume(@PathVariable Long id) {
        CareersService.Resume resume = careersService.resume(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.application().getResumeContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resume.application().getResumeFileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(resume.content());
    }

    private JobResponse withCount(JobPosting job) {
        return JobResponse.forAdmin(job, careersService.applicationCounts().getOrDefault(job.getId(), 0L));
    }
}
