package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.FormDtos.FormQuestion;
import com.narturebelle.providers.dto.FormDtos.QuestionType;
import com.narturebelle.providers.model.ApplicationAnswer;
import com.narturebelle.providers.model.ProviderApplication;
import com.narturebelle.providers.repository.ApplicationAnswerRepository;
import com.narturebelle.providers.security.FieldEncryptor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes answers, encrypting sensitive ones so they never sit in the database as plain text. */
@Component
public class ApplicationAnswerStore {

    private final ApplicationAnswerRepository answerRepository;
    private final ApplicationFormCatalog catalog;
    private final FieldEncryptor encryptor;

    public ApplicationAnswerStore(ApplicationAnswerRepository answerRepository, ApplicationFormCatalog catalog, FieldEncryptor encryptor) {
        this.answerRepository = answerRepository;
        this.catalog = catalog;
        this.encryptor = encryptor;
    }

    /** Stored values, with sensitive answers decrypted. Only reviewer screens and internal rules may use this. */
    public Map<String, String> readAll(ProviderApplication application) {
        Map<String, FormQuestion> byKey = catalog.questionsByKey(application.getProfessionalType());
        Map<String, String> values = new HashMap<>();
        for (ApplicationAnswer answer : answerRepository.findByApplicationId(application.getId())) {
            FormQuestion question = byKey.get(answer.getQuestionKey());
            boolean sensitive = question != null && question.sensitive();
            values.put(answer.getQuestionKey(), sensitive ? encryptor.decrypt(answer.getAnswerValue()) : answer.getAnswerValue());
        }
        return values;
    }

    public String read(ProviderApplication application, String key) {
        return answerRepository.findByApplicationIdAndQuestionKey(application.getId(), key)
                .map(ApplicationAnswer::getAnswerValue).orElse(null);
    }

    public void put(Long applicationId, String key, String value, boolean sensitive) {
        ApplicationAnswer answer = answerRepository.findByApplicationIdAndQuestionKey(applicationId, key).orElseGet(() -> {
            ApplicationAnswer created = new ApplicationAnswer();
            created.setApplicationId(applicationId);
            created.setQuestionKey(key);
            return created;
        });
        answer.setAnswerValue(sensitive ? encryptor.encrypt(value) : value);
        answerRepository.save(answer);
    }

    public void remove(Long applicationId, String key) {
        answerRepository.findByApplicationIdAndQuestionKey(applicationId, key).ifPresent(answerRepository::delete);
    }

    /** A stored value as the API shows it: a multi-select becomes a list. */
    public static Object toApiValue(FormQuestion question, String stored) {
        return question != null && question.type() == QuestionType.MULTI_SELECT ? splitMulti(stored) : stored;
    }

    public static List<String> splitMulti(String stored) {
        return stored == null || stored.isBlank() ? List.of() : Arrays.asList(stored.split("\n"));
    }
}
