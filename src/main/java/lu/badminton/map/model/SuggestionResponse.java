package lu.badminton.map.model;

import java.util.List;

// The full response for a club-suggestion lookup: the resolved address and
// the ranked list of nearby clubs. `fallback` is true when criteria were
// given (Hobby/Competition and/or training days) but no club met all of them:
// `suggestions` is then the 3 nearest clubs regardless, each carrying its own
// `mismatches` so the person can see what didn't fit.
public record SuggestionResponse(GeocodeResult resolved, List<ClubSuggestion> suggestions, boolean fallback) {
}
