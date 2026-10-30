package com.narturebelle.service;

import com.narturebelle.dto.JobApplicationForm;
import com.narturebelle.dto.JobRequest;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.exception.CareersException;
import com.narturebelle.model.JobApplication;
import com.narturebelle.model.JobApplicationResume;
import com.narturebelle.model.JobPosting;
import com.narturebelle.model.JobStatus;
import com.narturebelle.repository.JobApplicationRepository;
import com.narturebelle.repository.JobApplicationResumeRepository;
import com.narturebelle.repository.JobPostingRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Careers page: admin-managed job postings, and applications with a résumé emailed to the admin inbox. */
@Service
public class CareersService {

    private static final Logger log = LoggerFactory.getLogger(CareersService.class);

    static final long MAX_RESUME_BYTES = 5L * 1024 * 1024;

    /** Allowed résumé types: extension -> (content type, leading magic bytes). The bytes are checked, not just the name. */
    private static final Map<String, ResumeType> RESUME_TYPES = Map.of(
            "pdf", new ResumeType("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
            "doc", new ResumeType("application/msword",
                    new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}),
            "docx", new ResumeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    new byte[]{'P', 'K', 0x03, 0x04}));

    private record ResumeType(String contentType, byte[] magic) {}

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final JobApplicationResumeRepository resumeRepository;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final String adminEmail;

    public CareersService(JobPostingRepository jobPostingRepository, JobApplicationRepository jobApplicationRepository,
                          JobApplicationResumeRepository resumeRepository, EmailSender emailSender, RateLimiter rateLimiter,
                          AuditService auditService, TransactionTemplate transactionTemplate,
                          @Value("${careers.admin-email}") String adminEmail) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.resumeRepository = resumeRepository;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
        this.adminEmail = adminEmail;
    }

    // ---- public ----

    public List<JobPosting> openJobs() {
        return jobPostingRepository.findByStatusOrderByPostedAtDesc(JobStatus.OPEN);
    }

    /** A closed job is 404 publicly, same as a missing one. */
    public JobPosting openJob(Long id) {
        return jobPostingRepository.findById(id)
                .filter(job -> job.getStatus() == JobStatus.OPEN)
                .orElseThrow(() -> jobNotFound(id));
    }

    public JobApplication apply(Long jobId, JobApplicationForm form, String clientIp) {
        JobPosting job = jobPostingRepository.findById(jobId).orElseThrow(() -> jobNotFound(jobId));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new CareersException(409, "JOB_CLOSED", "This role is no longer accepting applications.");
        }
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!rateLimiter.tryAcquire("apply-ip:" + clientIp, 10, Duration.ofHours(1))
                || !rateLimiter.tryAcquire("apply-email:" + email, 5, Duration.ofDays(1))) {
            throw new CareersException(429, "TOO_MANY_REQUESTS", "Too many applications. Please try again later.");
        }

        String extension = resumeExtension(form.getResume());
        byte[] content = resumeBytes(form.getResume(), extension);

        JobApplication application = new JobApplication();
        application.setJobId(jobId);
        application.setFullName(form.getFullName().trim());
        application.setEmail(email);
        application.setPhone(form.getPhone().trim());
        application.setLocation(blankToNull(form.getLocation()));
        application.setLinkedinUrl(blankToNull(form.getLinkedinUrl()));
        application.setPortfolioUrl(blankToNull(form.getPortfolioUrl()));
        application.setYearsExperience(form.getYearsExperience());
        application.setCoverNote(blankToNull(form.getCoverNote()));
        application.setResumeFileName(safeFileName(form.getResume().getOriginalFilename(), extension));
        application.setResumeContentType(RESUME_TYPES.get(extension).contentType());
        application.setResumeSizeBytes(content.length);

        JobApplication saved = transactionTemplate.execute(status -> {
            JobApplication row = jobApplicationRepository.save(application);
            resumeRepository.save(new JobApplicationResume(row.getId(), content));
            return row;
        });
        auditService.record("JOB_APPLICATION_SUBMITTED", "JOB_APPLICATION", String.valueOf(saved.getId()),
                "Application received for job " + jobId);

        notifyAdmin(job, saved, content);
        sendQuietly(new EmailMessage(saved.getEmail(), null, "We received your application -- " + oneLine(job.getTitle()),
                "Hi " + saved.getFullName() + ",\n\nThank you for applying for " + job.getTitle()
                        + " at NartureBelle. Our team will review your application and get back to you if it's a match.\n\n"
                        + "-- The NartureBelle team", null));
        return saved;
    }

    // ---- admin ----

    public List<JobPosting> allJobs() {
        return jobPostingRepository.findAllByOrderByPostedAtDesc();
    }

    public Map<Long, Long> applicationCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : jobApplicationRepository.countByJob()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public JobPosting create(JobRequest req) {
        JobPosting job = new JobPosting();
        apply(job, req);
        job.setStatus(JobStatus.OPEN);
        JobPosting saved = jobPostingRepository.save(job);
        auditService.record("JOB_POSTED", "JOB_POSTING", String.valueOf(saved.getId()), "Admin posted a role");
        return saved;
    }

    public JobPosting replace(Long id, JobRequest req) {
        JobPosting job = find(id);
        apply(job, req);
        JobPosting saved = jobPostingRepository.save(job);
        auditService.record("JOB_UPDATED", "JOB_POSTING", String.valueOf(id), "Admin edited a role");
        return saved;
    }

    public JobPosting setOpen(Long id, boolean open) {
        JobPosting job = find(id);
        job.setStatus(open ? JobStatus.OPEN : JobStatus.CLOSED);
        job.setClosedAt(open ? null : LocalDateTime.now());
        JobPosting saved = jobPostingRepository.save(job);
        auditService.record(open ? "JOB_REOPENED" : "JOB_CLOSED", "JOB_POSTING", String.valueOf(id),
                open ? "Admin reopened a role" : "Admin closed a role");
        return saved;
    }

    public List<JobApplication> applications(Long jobId) {
        find(jobId);
        return jobApplicationRepository.findByJobIdOrderBySubmittedAtDesc(jobId);
    }

    /** The application and its résumé bytes; audited, since it's personal data. */
    public Resume resume(Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new CareersException(404, "APPLICATION_NOT_FOUND", "Application " + applicationId + " not found."));
        byte[] content = resumeRepository.findById(applicationId)
                .orElseThrow(() -> new CareersException(404, "APPLICATION_NOT_FOUND", "Résumé for application " + applicationId + " not found."))
                .getContent();
        auditService.record("JOB_APPLICATION_RESUME_DOWNLOADED", "JOB_APPLICATION", String.valueOf(applicationId),
                "Admin downloaded a résumé");
        return new Resume(application, content);
    }

    public record Resume(JobApplication application, byte[] content) {}

    // ---- helpers ----

    private JobPosting find(Long id) {
        return jobPostingRepository.findById(id).orElseThrow(() -> jobNotFound(id));
    }

    private static CareersException jobNotFound(Long id) {
        return new CareersException(404, "JOB_NOT_FOUND", "Job " + id + " not found.");
    }

    private static void apply(JobPosting job, JobRequest req) {
        job.setTitle(req.getTitle().trim());
        job.setTeam(blankToNull(req.getTeam()));
        job.setLocation(req.getLocation().trim());
        job.setEmploymentType(req.getEmploymentType());
        job.setWorkMode(req.getWorkMode());
        job.setSummary(req.getSummary().trim());
        job.setDescription(req.getDescription().strip());
        job.setRequirements(new ArrayList<>(req.getRequirements() == null ? List.of()
                : req.getRequirements().stream().map(String::trim).toList()));
    }

    private static String resumeExtension(MultipartFile resume) {
        if (resume == null || resume.isEmpty()) {
            throw new CareersException(400, "VALIDATION_FAILED", "Please attach your résumé.", "resume");
        }
        if (resume.getSize() > MAX_RESUME_BYTES) {
            throw new CareersException(413, "RESUME_TOO_LARGE", "Résumé must be 5 MB or smaller.");
        }
        String name = resume.getOriginalFilename() == null ? "" : resume.getOriginalFilename();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!RESUME_TYPES.containsKey(extension)) {
            throw resumeTypeNotAllowed();
        }
        return extension;
    }

    private static byte[] resumeBytes(MultipartFile resume, String extension) {
        byte[] content;
        try {
            content = resume.getBytes();
        } catch (IOException e) {
            throw new CareersException(400, "VALIDATION_FAILED", "Could not read the résumé file.", "resume");
        }
        byte[] magic = RESUME_TYPES.get(extension).magic();
        if (content.length < magic.length || !Arrays.equals(Arrays.copyOf(content, magic.length), magic)) {
            throw resumeTypeNotAllowed();
        }
        return content;
    }

    private static CareersException resumeTypeNotAllowed() {
        return new CareersException(400, "RESUME_TYPE_NOT_ALLOWED", "Résumé must be a PDF, DOC or DOCX file.");
    }

    /** Keeps the applicant's file name readable but safe for a Content-Disposition header and an email attachment. */
    private static String safeFileName(String original, String extension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (name.length() > 150) {
            name = name.substring(0, 140) + "." + extension;
        }
        return name.isEmpty() || name.startsWith(".") ? "resume." + extension : name;
    }

    private void notifyAdmin(JobPosting job, JobApplication a, byte[] resume) {
        String text = "New application for " + job.getTitle() + " (job #" + job.getId() + ")\n\n"
                + "Name: " + a.getFullName() + "\n"
                + "Email: " + a.getEmail() + "\n"
                + "Phone: " + a.getPhone() + "\n"
                + "Location: " + orDash(a.getLocation()) + "\n"
                + "LinkedIn: " + orDash(a.getLinkedinUrl()) + "\n"
                + "Portfolio: " + orDash(a.getPortfolioUrl()) + "\n"
                + "Years of experience: " + (a.getYearsExperience() == null ? "-" : a.getYearsExperience()) + "\n"
                + "Submitted: " + a.getSubmittedAt() + "\n\n"
                + "Cover note:\n" + orDash(a.getCoverNote()) + "\n\n"
                + "Résumé attached: " + a.getResumeFileName() + "\n"
                + "Application id " + a.getId() + " -- also visible in the admin careers screen.";
        boolean sent = sendQuietly(new EmailMessage(adminEmail, a.getEmail(),
                "New application: " + oneLine(job.getTitle()) + " -- " + oneLine(a.getFullName()), text,
                List.of(new EmailMessage.Attachment(a.getResumeFileName(), resume))));
        if (sent) {
            a.setAdminNotifiedAt(LocalDateTime.now());
            jobApplicationRepository.save(a);
        }
    }

    /** True only if the email really went out. A failure never fails the submission -- it's already saved. */
    boolean sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
            return emailSender.delivers();
        } catch (Exception e) {
            log.warn("Email send failed ({}): {}", message.subject(), e.getMessage());
            return false;
        }
    }

    static String oneLine(String s) {
        return s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
