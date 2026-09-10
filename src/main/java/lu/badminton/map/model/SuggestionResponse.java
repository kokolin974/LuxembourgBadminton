package lu.badminton.map.model;

import java.util.List;

// The full response for a club-suggestion lookup: the resolved address and
// the ranked list of nearby clubs.
public record SuggestionResponse(GeocodeResult resolved, List<ClubSuggestion> suggestions) {
}
