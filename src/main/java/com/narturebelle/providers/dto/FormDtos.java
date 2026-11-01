package com.narturebelle.providers.dto;

import java.util.List;
import java.util.Set;

/** The adaptive application form: the UI renders these, and shows a question only when showWhenKey holds one of showWhenValues. */
public final class FormDtos {

    private FormDtos() {}

    public enum QuestionType { TEXT, LONG_TEXT, NUMBER, YEAR, DATE, YES_NO, SELECT, MULTI_SELECT, FILE, CONFIRM }

    public record FormOption(String value, String label) {}

    public record FormQuestion(String key, String label, QuestionType type, List<FormOption> options, boolean required,
                               boolean sensitive, String hint, String showWhenKey, Set<String> showWhenValues,
                               String pattern, String patternMessage) {}

    public record FormSection(String key, String title, String description, List<FormQuestion> questions) {}

    public record ProfessionOption(String value, String label) {}

    public record ApplyInfoResponse(List<ProfessionOption> professions, String termsVersion, String termsText, boolean draft) {}
}
