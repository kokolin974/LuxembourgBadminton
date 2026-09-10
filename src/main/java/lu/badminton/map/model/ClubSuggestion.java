package lu.badminton.map.model;

// One suggested club for a given address: the club's data for the year asked
// about, the straight-line distance to it, and whether the address falls
// inside that club's area-of-influence radius. `withinRange` is null when the
// club has no defined radius (no level and no override) — "not set", distinct
// from a definite "out of range".
public record ClubSuggestion(ClubYearView club, double distanceKm, Boolean withinRange) {
}
