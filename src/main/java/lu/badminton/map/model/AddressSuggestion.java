package lu.badminton.map.model;

// One address-autocomplete result: a human-readable label plus the
// coordinates Photon returned for it, so selecting a suggestion needs no
// further geocoding round-trip.
public record AddressSuggestion(String label, double latitude, double longitude) {
}
