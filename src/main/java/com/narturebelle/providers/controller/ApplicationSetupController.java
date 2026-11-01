package com.narturebelle.providers.controller;

import com.narturebelle.providers.dto.SetupDtos.AgreementResponse;
import com.narturebelle.providers.dto.SetupDtos.BankDetailsRequest;
import com.narturebelle.providers.dto.SetupDtos.BankDetailsResponse;
import com.narturebelle.providers.dto.SetupDtos.SetupStatusResponse;
import com.narturebelle.providers.dto.SetupDtos.SignAgreementRequest;
import com.narturebelle.providers.dto.SetupDtos.SubmitTrainingRequest;
import com.narturebelle.providers.dto.SetupDtos.TrainingResponse;
import com.narturebelle.providers.dto.SetupDtos.TrainingResult;
import com.narturebelle.providers.service.ApplicationSetupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** After approval: the agreement, bank and tax details, training and profile confirmation. */
@RestController
@RequestMapping("/api/v1/provider-application/setup")
public class ApplicationSetupController {

    private final ApplicationSetupService setupService;

    public ApplicationSetupController(ApplicationSetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping
    public ResponseEntity<SetupStatusResponse> status() {
        return ResponseEntity.ok(setupService.status());
    }

    @GetMapping("/agreement")
    public ResponseEntity<AgreementResponse> agreement() {
        return ResponseEntity.ok(setupService.agreement());
    }

    @PostMapping("/agreement")
    public ResponseEntity<AgreementResponse> sign(@Valid @RequestBody SignAgreementRequest req) {
        return ResponseEntity.ok(setupService.signAgreement(req.typedName()));
    }

    @GetMapping("/bank")
    public ResponseEntity<BankDetailsResponse> bank() {
        BankDetailsResponse bank = setupService.bank();
        return bank == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(bank);
    }

    @PutMapping("/bank")
    public ResponseEntity<BankDetailsResponse> saveBank(@Valid @RequestBody BankDetailsRequest req) {
        return ResponseEntity.ok(setupService.saveBank(req));
    }

    @GetMapping("/training")
    public ResponseEntity<TrainingResponse> training() {
        return ResponseEntity.ok(setupService.training());
    }

    @PostMapping("/training")
    public ResponseEntity<TrainingResult> submitTraining(@Valid @RequestBody SubmitTrainingRequest req) {
        return ResponseEntity.ok(setupService.submitTraining(req.answers()));
    }

    @PostMapping("/profile-confirmation")
    public ResponseEntity<SetupStatusResponse> confirmProfile() {
        return ResponseEntity.ok(setupService.confirmProfile());
    }
}
