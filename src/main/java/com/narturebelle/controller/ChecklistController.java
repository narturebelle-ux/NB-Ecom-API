package com.narturebelle.controller;

import com.narturebelle.model.ChecklistTask;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.repository.ChecklistTaskRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/checklists")
public class ChecklistController {

    private final ChecklistTaskRepository checklistTaskRepository;

    public ChecklistController(ChecklistTaskRepository checklistTaskRepository) {
        this.checklistTaskRepository = checklistTaskRepository;
    }

    @GetMapping
    public ResponseEntity<List<ChecklistTask>> getChecklists(
            @RequestParam(required = false) PregnancyStage stage) {
        if (stage != null) {
            return ResponseEntity.ok(checklistTaskRepository.findByStage(stage));
        }
        return ResponseEntity.ok(checklistTaskRepository.findAll());
    }

    @GetMapping("/stage/{stage}")
    public ResponseEntity<List<ChecklistTask>> getChecklistByStage(@PathVariable PregnancyStage stage) {
        return ResponseEntity.ok(checklistTaskRepository.findByStage(stage));
    }
}
