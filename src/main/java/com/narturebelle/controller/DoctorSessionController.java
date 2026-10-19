package com.narturebelle.controller;

import com.narturebelle.dto.DoctorRsvpRequest;
import com.narturebelle.model.DoctorRsvp;
import com.narturebelle.model.DoctorSession;
import com.narturebelle.repository.DoctorRsvpRepository;
import com.narturebelle.repository.DoctorSessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/doctor-sessions")
public class DoctorSessionController {

    private final DoctorSessionRepository doctorSessionRepository;
    private final DoctorRsvpRepository doctorRsvpRepository;

    public DoctorSessionController(DoctorSessionRepository doctorSessionRepository,
                                  DoctorRsvpRepository doctorRsvpRepository) {
        this.doctorSessionRepository = doctorSessionRepository;
        this.doctorRsvpRepository = doctorRsvpRepository;
    }

    @GetMapping
    public ResponseEntity<List<DoctorSession>> getDoctorSessions() {
        return ResponseEntity.ok(doctorSessionRepository.findAll());
    }

    @PostMapping("/rsvp")
    public ResponseEntity<Map<String, Object>> rsvpSession(@RequestBody DoctorRsvpRequest request) {
        DoctorRsvp rsvp = new DoctorRsvp();
        rsvp.setSessionId(request.getSessionId());
        rsvp.setMotherName(request.getMotherName());
        rsvp.setPhone(request.getPhone());
        rsvp.setEmail(request.getEmail());
        rsvp.setStage(request.getStage());
        rsvp.setUserQuestion(request.getUserQuestion());

        DoctorRsvp saved = doctorRsvpRepository.save(rsvp);

        // Increment attendee count
        if (request.getSessionId() != null) {
            doctorSessionRepository.findById(request.getSessionId()).ifPresent(session -> {
                session.setAttendeeCount((session.getAttendeeCount() != null ? session.getAttendeeCount() : 0) + 1);
                doctorSessionRepository.save(session);
            });
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("rsvpId", saved.getId());
        response.put("message", "Your seat is confirmed! An SMS & WhatsApp reminder with the Zoom link will be sent prior to the session.");
        return ResponseEntity.ok(response);
    }
}
