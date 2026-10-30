package com.narturebelle.service;

import com.narturebelle.dto.FeedbackRequest;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.exception.CareersException;
import com.narturebelle.model.FeedbackMessage;
import com.narturebelle.repository.FeedbackMessageRepository;
import com.narturebelle.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/** Website "ideas / feedback" form: saved, then emailed to the admin inbox. */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final FeedbackMessageRepository feedbackMessageRepository;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final String adminEmail;

    public FeedbackService(FeedbackMessageRepository feedbackMessageRepository, EmailSender emailSender,
                           RateLimiter rateLimiter, @Value("${careers.admin-email}") String adminEmail) {
        this.feedbackMessageRepository = feedbackMessageRepository;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.adminEmail = adminEmail;
    }

    public FeedbackMessage submit(FeedbackRequest req, Long userId, String clientIp) {
        if (!rateLimiter.tryAcquire("feedback-ip:" + clientIp, 10, Duration.ofHours(1))) {
            throw new CareersException(429, "TOO_MANY_REQUESTS", "Too many messages. Please try again later.");
        }
        FeedbackMessage feedback = new FeedbackMessage();
        feedback.setUserId(userId);
        feedback.setName(blankToNull(req.getName()));
        feedback.setEmail(blankToNull(req.getEmail()));
        feedback.setMessage(req.getMessage().strip());
        feedback.setRating(req.getRating());
        feedback.setPage(blankToNull(req.getPage()));
        FeedbackMessage saved = feedbackMessageRepository.save(feedback);

        String text = "New idea / feedback from the website\n\n"
                + "From: " + (saved.getName() == null ? "-" : saved.getName())
                + (saved.getEmail() == null ? "" : " <" + saved.getEmail() + ">") + "\n"
                + "Signed-in user id: " + (userId == null ? "-" : userId) + "\n"
                + "Rating: " + (saved.getRating() == null ? "-" : saved.getRating() + "/5") + "\n"
                + "Page: " + (saved.getPage() == null ? "-" : saved.getPage()) + "\n\n"
                + saved.getMessage();
        String subject = "Website feedback" + (saved.getRating() == null ? "" : " (" + saved.getRating() + "/5)")
                + (saved.getName() == null ? "" : " from " + CareersService.oneLine(saved.getName()));
        try {
            emailSender.send(new EmailMessage(adminEmail, saved.getEmail(), subject, text, null));
            if (emailSender.delivers()) {
                saved.setAdminNotifiedAt(LocalDateTime.now());
                saved = feedbackMessageRepository.save(saved);
            }
        } catch (Exception e) {
            log.warn("Feedback email failed for feedback {}: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
