package com.narturebelle.controller;

import com.narturebelle.model.ConsentDocument;
import com.narturebelle.repository.ConsentDocumentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/consent-documents")
public class ConsentDocumentController {

    private final ConsentDocumentRepository consentDocumentRepository;

    public ConsentDocumentController(ConsentDocumentRepository consentDocumentRepository) {
        this.consentDocumentRepository = consentDocumentRepository;
    }

    /** Public — the active version of every consent document type (TERMS, PRIVACY). */
    @GetMapping("/current")
    public ResponseEntity<List<ConsentDocument>> getCurrent() {
        return ResponseEntity.ok(consentDocumentRepository.findByActiveTrue());
    }
}
