package com.narturebelle.providers.service;

import com.narturebelle.providers.dto.SetupDtos.TrainingQuestion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** The go-live quiz: privacy, emergency escalation, sex-disclosure law, product suggestions. DRAFT pending legal review. */
@Component
public class TrainingCatalog {

    private record Item(TrainingQuestion question, int correctOption) {}

    private static final List<Item> ITEMS = List.of(
            new Item(new TrainingQuestion("privacy", "Privacy", "A mother's details can be shared with:",
                    List.of("Anyone who asks on her behalf", "Only people she has allowed, within her care", "Other mothers, without her name")), 1),
            new Item(new TrainingQuestion("emergency", "Emergency escalation", "A mother describes heavy bleeding during an online session. You should:",
                    List.of("Book her a follow-up next week", "Tell her to go to emergency care now, and stay with her until she has help", "Send her a product suggestion")), 1),
            new Item(new TrainingQuestion("sex", "Sex-disclosure law", "A mother asks whether her baby is a boy or a girl. You should:",
                    List.of("Tell her if you know", "Give her a hint", "Refuse: revealing a baby's sex is a criminal offence")), 2),
            new Item(new TrainingQuestion("products", "Product suggestions", "You should suggest a product when:",
                    List.of("It fits what she shared in the session, and any financial tie is declared", "It pays you the most", "A company asks you to")), 0));

    public List<TrainingQuestion> questions() {
        return ITEMS.stream().map(Item::question).toList();
    }

    /** Every question must be right to pass. */
    public boolean passes(Map<String, Integer> answers) {
        return ITEMS.stream().allMatch(item -> Integer.valueOf(item.correctOption()).equals(answers.get(item.question().id())));
    }

    public int score(Map<String, Integer> answers) {
        return (int) ITEMS.stream().filter(item -> Integer.valueOf(item.correctOption()).equals(answers.get(item.question().id()))).count();
    }
}
