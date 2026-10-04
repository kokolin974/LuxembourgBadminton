package lu.badminton.map.model;

import java.util.List;

// One suggested club for a given address: the club's data for the year asked
// about, the straight-line distance to it, and whether the address falls
// inside that club's area-of-influence radius. `withinRange` is null when the
// club has no defined radius (no level and no override) — "not set", distinct
// from a definite "out of range".
//
// `matchedDays` lists which of the requested training days this club actually
// trains on (empty when no days were requested). `mismatches` explains, in
// plain words, every criterion (Hobby/Competition, training days) the club
// does NOT meet — empty for a club that satisfies everything asked.
public record ClubSuggestion(ClubYearView club, double distanceKm, Boolean withinRange,
                             List<String> matchedDays, List<String> mismatches) {
}
