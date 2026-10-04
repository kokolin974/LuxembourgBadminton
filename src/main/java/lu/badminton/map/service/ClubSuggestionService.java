package lu.badminton.map.service;

import lu.badminton.map.model.ClubSuggestion;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.ClubYearView;
import lu.badminton.map.model.GeocodeResult;
import lu.badminton.map.model.SuggestionResponse;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Suggests clubs that are a good fit for an address. For now "fit" is purely
// geographic — the nearest clubs by straight-line distance — with each
// result flagged for whether the address falls inside the club's radius of
// influence. Future criteria (level, weekly availability, ...) would become
// extra weighted terms in the ranking here plus extra params on the endpoint.
@Service
public class ClubSuggestionService {

    // How many clubs to suggest. A constant for now; the natural place to
    // make it configurable later.
    private static final int SUGGESTION_COUNT = 3;

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final ClubService clubService;
    private final GeocodingService geocodingService;

    public ClubSuggestionService(ClubService clubService, GeocodingService geocodingService) {
        this.clubService = clubService;
        this.geocodingService = geocodingService;
    }

    // Empty when there's nothing to rank against — no usable point and an
    // address that couldn't be geocoded. Otherwise the resolved point plus
    // up to SUGGESTION_COUNT nearest clubs for the given year (latest year on
    // file when year is null).
    //
    // When lat/lon are supplied (an autocomplete suggestion was picked, and
    // it already carries coordinates) they're used directly and no geocoding
    // happens; `label` is the text to echo back for that point. Otherwise the
    // free-text `address` is geocoded via Nominatim.
    public Optional<SuggestionResponse> suggest(String address, Double lat, Double lon,
                                                 String label, Integer year) {
        GeocodeResult point;
        if (lat != null && lon != null) {
            String resolvedLabel = (label != null && !label.isBlank()) ? label : address;
            point = new GeocodeResult(resolvedLabel != null ? resolvedLabel : "Selected location", lat, lon);
        } else {
            Optional<GeocodeResult> resolved = geocodingService.geocode(address);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            point = resolved.get();
        }

        // Non-affiliated clubs are shown on the map but never recommended here.
        List<ClubSuggestion> suggestions = clubService.findAllForYear(year).stream()
                .filter(club -> !ClubYear.FILIERE_NON_AFFILIE.equals(club.getFiliere()))
                .map(club -> toSuggestion(point, club))
                .sorted(Comparator.comparingDouble(ClubSuggestion::distanceKm))
                .limit(SUGGESTION_COUNT)
                .toList();

        return Optional.of(new SuggestionResponse(point, suggestions));
    }

    private ClubSuggestion toSuggestion(GeocodeResult point, ClubYearView club) {
        double distanceKm = haversineKm(point.latitude(), point.longitude(),
                club.getLatitude(), club.getLongitude());
        Double radiusKm = club.getEffectiveRadiusKm();
        // null (not false) when the club has no defined radius — the frontend
        // shows "range not set" rather than a misleading "out of range".
        Boolean withinRange = radiusKm == null ? null : distanceKm <= radiusKm;
        return new ClubSuggestion(club, round1(distanceKm), withinRange);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
