package lu.badminton.map.model;

// A geocoded address: the human-readable place Nominatim matched, plus its
// coordinates. `label` is echoed back to the user so they can confirm the
// lookup landed where they meant.
public record GeocodeResult(String label, double latitude, double longitude) {
}
