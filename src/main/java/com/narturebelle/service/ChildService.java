package com.narturebelle.service;

import com.narturebelle.dto.ChildVaccinationsResponse;
import com.narturebelle.dto.CreateChildRequest;
import com.narturebelle.dto.PatchChildRequest;
import com.narturebelle.dto.RecordVaccinationRequest;
import com.narturebelle.dto.VaccinationItemResponse;
import com.narturebelle.exception.ChildNotFoundException;
import com.narturebelle.exception.InvalidChildRequestException;
import com.narturebelle.exception.VaccineScheduleItemNotFoundException;
import com.narturebelle.model.Child;
import com.narturebelle.model.ChildVaccination;
import com.narturebelle.model.VaccineScheduleItem;
import com.narturebelle.model.VaccineScheduleVersion;
import com.narturebelle.repository.ChildRepository;
import com.narturebelle.repository.ChildVaccinationRepository;
import com.narturebelle.repository.VaccineScheduleItemRepository;
import com.narturebelle.repository.VaccineScheduleVersionRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChildService {

    private final ChildRepository childRepository;
    private final VaccineScheduleVersionRepository vaccineScheduleVersionRepository;
    private final VaccineScheduleItemRepository vaccineScheduleItemRepository;
    private final ChildVaccinationRepository childVaccinationRepository;
    private final AuditService auditService;

    public ChildService(ChildRepository childRepository,
                        VaccineScheduleVersionRepository vaccineScheduleVersionRepository,
                        VaccineScheduleItemRepository vaccineScheduleItemRepository,
                        ChildVaccinationRepository childVaccinationRepository,
                        AuditService auditService) {
        this.childRepository = childRepository;
        this.vaccineScheduleVersionRepository = vaccineScheduleVersionRepository;
        this.vaccineScheduleItemRepository = vaccineScheduleItemRepository;
        this.childVaccinationRepository = childVaccinationRepository;
        this.auditService = auditService;
    }

    public List<Child> listChildren(Long userId) {
        return childRepository.findByUserIdOrderByDateOfBirthAsc(userId);
    }

    public Child createChild(Long userId, CreateChildRequest req) {
        Child child = new Child();
        child.setUserId(userId);
        child.setName(req.getName().trim());
        child.setDateOfBirth(req.getDateOfBirth());
        child.setSex(req.getSex());
        Child saved = childRepository.save(child);
        // No name or date of birth in the audit row -- only that a profile exists.
        auditService.record("CHILD_CREATED", "CHILD", String.valueOf(saved.getId()), "User added a child profile");
        return saved;
    }

    public Child patchChild(Long userId, Long childId, PatchChildRequest req) {
        Child child = requireOwnChild(userId, childId);
        if (req.getName() != null) {
            child.setName(req.getName().trim());
        }
        if (req.getDateOfBirth() != null) {
            child.setDateOfBirth(req.getDateOfBirth());
        }
        if (req.getSex() != null) {
            child.setSex(req.getSex());
        }
        Child saved = childRepository.save(child);
        auditService.record("CHILD_UPDATED", "CHILD", String.valueOf(saved.getId()), "User updated a child profile");
        return saved;
    }

    public ChildVaccinationsResponse vaccinations(Long userId, Long childId) {
        Child child = requireOwnChild(userId, childId);
        VaccineScheduleVersion version = vaccineScheduleVersionRepository.findFirstByActiveTrueOrderByVersionDesc().orElse(null);
        if (version == null) {
            return ChildVaccinationsResponse.of(child.getId(), null, List.of());
        }
        Map<Long, ChildVaccination> givenByItem = childVaccinationRepository.findByChildId(child.getId()).stream()
                .collect(Collectors.toMap(ChildVaccination::getScheduleItemId, Function.identity()));
        LocalDate today = LocalDate.now();
        List<VaccinationItemResponse> items = vaccineScheduleItemRepository
                .findByScheduleVersionIdOrderBySortOrderAsc(version.getId()).stream()
                .map(item -> VaccinationItemResponse.from(item, child.getDateOfBirth(), givenByItem.get(item.getId()), today))
                .toList();
        return ChildVaccinationsResponse.of(child.getId(), version, items);
    }

    /** Idempotent upsert: recording the same dose again overwrites givenAt/givenWhere rather than adding a row. */
    @Transactional
    public VaccinationItemResponse recordVaccination(Long userId, Long childId, Long scheduleItemId, RecordVaccinationRequest req) {
        Child child = requireOwnChild(userId, childId);
        VaccineScheduleItem item = requireActiveScheduleItem(scheduleItemId);
        if (req.getGivenAt().isBefore(child.getDateOfBirth())) {
            throw new InvalidChildRequestException("givenAt", "must not be before the child's date of birth");
        }
        ChildVaccination vaccination = childVaccinationRepository.findByChildIdAndScheduleItemId(child.getId(), item.getId())
                .orElseGet(() -> {
                    ChildVaccination fresh = new ChildVaccination();
                    fresh.setChildId(child.getId());
                    fresh.setScheduleItemId(item.getId());
                    return fresh;
                });
        vaccination.setGivenAt(req.getGivenAt());
        vaccination.setGivenWhere(req.getGivenWhere());
        ChildVaccination saved = childVaccinationRepository.save(vaccination);
        auditService.record("CHILD_VACCINATION_RECORDED", "CHILD_VACCINATION", String.valueOf(saved.getId()),
                "User recorded a vaccination as given", Map.of("childId", child.getId(), "scheduleItemId", item.getId()));
        return VaccinationItemResponse.from(item, child.getDateOfBirth(), saved, LocalDate.now());
    }

    /** Idempotent: removing a dose that was never recorded is a no-op, not a 404. */
    @Transactional
    public void removeVaccination(Long userId, Long childId, Long scheduleItemId) {
        Child child = requireOwnChild(userId, childId);
        VaccineScheduleItem item = requireActiveScheduleItem(scheduleItemId);
        childVaccinationRepository.findByChildIdAndScheduleItemId(child.getId(), item.getId()).ifPresent(vaccination -> {
            childVaccinationRepository.delete(vaccination);
            auditService.record("CHILD_VACCINATION_REMOVED", "CHILD_VACCINATION", String.valueOf(vaccination.getId()),
                    "User un-marked a vaccination", Map.of("childId", child.getId(), "scheduleItemId", item.getId()));
        });
    }

    private Child requireOwnChild(Long userId, Long childId) {
        return childRepository.findByIdAndUserId(childId, userId)
                .orElseThrow(() -> new ChildNotFoundException("Child " + childId + " not found."));
    }

    private VaccineScheduleItem requireActiveScheduleItem(Long scheduleItemId) {
        VaccineScheduleVersion version = vaccineScheduleVersionRepository.findFirstByActiveTrueOrderByVersionDesc().orElse(null);
        return vaccineScheduleItemRepository.findById(scheduleItemId)
                .filter(item -> version != null && item.getScheduleVersionId().equals(version.getId()))
                .orElseThrow(() -> new VaccineScheduleItemNotFoundException(
                        "Vaccine schedule item " + scheduleItemId + " is not in the active schedule."));
    }
}
