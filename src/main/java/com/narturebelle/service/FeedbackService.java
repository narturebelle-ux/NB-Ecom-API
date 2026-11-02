package com.narturebelle.service;

import com.narturebelle.dto.FeedbackRequest;
import com.narturebelle.mail.MailQueue;
import com.narturebelle.exception.CareersException;
import com.narturebelle.model.FeedbackMessage;
import com.narturebelle.repository.FeedbackMessageRepository;
import com.narturebelle.security.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/** Website "ideas / feedback" form: saved, then emailed to the admin inbox. */
@Service
public class FeedbackService {


    private final FeedbackMessageRepository feedbackMessageRepository;
    private final MailQueue mailQueue;
    private final RateLimiter rateLimiter;
    private final String adminEmail;

    public FeedbackService(FeedbackMessageRepository feedbackMessageRepository, MailQueue mailQueue,
                           RateLimiter rateLimiter, @Value("${careers.admin-email}") String adminEmail) {
        this.feedbackMessageRepository = feedbackMessageRepository;
        this.mailQueue = mailQueue;
        this.rateLimiter = rateLimiter;
        this.adminEmail = adminEmail;
    }

    @Transactional
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
        mailQueue.sendFeedbackNotice(saved.getId(), adminEmail, saved.getEmail(), subject, text);
        return saved;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
