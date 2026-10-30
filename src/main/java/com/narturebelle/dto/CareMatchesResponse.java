package com.narturebelle.dto;

import com.narturebelle.model.Modality;

import java.util.List;

/** GET /me/care/matches: the criteria actually applied, then matching providers, earliest available first. */
public class CareMatchesResponse {

    private Criteria criteria;
    private List<CareMatch> matches;

    public static CareMatchesResponse of(List<String> languages, String city, Modality modality, List<CareMatch> matches) {
        CareMatchesResponse dto = new CareMatchesResponse();
        dto.criteria = new Criteria(languages, city, modality);
        dto.matches = matches;
        return dto;
    }

    public Criteria getCriteria() { return criteria; }
    public List<CareMatch> getMatches() { return matches; }

    /** modality null = online or in person. */
    public record Criteria(List<String> languages, String city, Modality modality) {}

    public record CareMatch(ProviderResponse provider, List<String> matchReasons, List<SlotResponse> nextSlots) {}
}
