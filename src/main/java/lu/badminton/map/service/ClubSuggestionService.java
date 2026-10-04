package lu.badminton.map.service;

import lu.badminton.map.model.ClubSuggestion;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.ClubYearView;
import lu.badminton.map.model.GeocodeResult;
import lu.badminton.map.model.ScheduleSlot;
import lu.badminton.map.model.SuggestionResponse;
import lu.badminton.map.repository.ScheduleSlotRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Suggests clubs that are a good fit for an address: the nearest clubs by
// straight-line distance, optionally narrowed by what the person wants —
// Hobby (filière "Active for life") or Competition (filière "Performance"),
// and/or one or more training days. Each result is also flagged for whether
// the address falls inside the club's radius of influence.
//
// When criteria are given but no club meets all of them, the 3 nearest clubs
// are returned anyway with an explanation of what doesn't match (see
// ClubSuggestion.mismatches), flagged with SuggestionResponse.fallback.
@Service
public class ClubSuggestionService {

    // How many clubs to suggest. A constant for now; the natural place to
    // make it configurable later.
    private static final int SUGGESTION_COUNT = 3;

    private static final double EARTH_RADIUS_KM = 6371.0088;

    // Days as stored on ScheduleSlot.dayOfWeek, in calendar order.
    private static final List<String> WEEK =
            List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");

    private final ClubService clubService;
    private final GeocodingService geocodingService;
    private final ScheduleSlotRepository scheduleSlotRepository;

    public ClubSuggestionService(ClubService clubService, GeocodingService geocodingService,
                                  ScheduleSlotRepository scheduleSlotRepository) {
        this.clubService = clubService;
        this.geocodingService = geocodingService;
        this.scheduleSlotRepository = scheduleSlotRepository;
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
    //
    // `type` is "hobby", "competition", or null/anything else for no
    // preference. `days` are weekday names; a club matches if it trains on AT
    // LEAST ONE of them, and a club with no schedule on file never matches
    // when days are requested. The schedule isn't year-versioned, so `year`
    // only decides which clubs exist, not which times apply.
    public Optional<SuggestionResponse> suggest(String address, Double lat, Double lon,
                                                 String label, Integer year,
                                                 String type, List<String> days) {
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

        String wantedFiliere = filiereForType(type);
        String typeName = typeName(type);
        List<String> wantedDays = normalizeDays(days);
        boolean hasCriteria = wantedFiliere != null || !wantedDays.isEmpty();
        Map<Long, Set<String>> trainingDays = wantedDays.isEmpty() ? Map.of() : trainingDaysByClub();

        // Non-affiliated clubs are shown on the map but never recommended here.
        List<ClubSuggestion> byDistance = clubService.findAllForYear(year).stream()
                .filter(club -> !ClubYear.FILIERE_NON_AFFILIE.equals(club.getFiliere()))
                .map(club -> evaluate(point, club, wantedFiliere, typeName, wantedDays, trainingDays))
                .sorted(Comparator.comparingDouble(ClubSuggestion::distanceKm))
                .toList();

        List<ClubSuggestion> matches = byDistance.stream()
                .filter(s -> s.mismatches().isEmpty())
                .limit(SUGGESTION_COUNT)
                .toList();

        if (matches.isEmpty() && hasCriteria && !byDistance.isEmpty()) {
            // Nothing meets every criterion — show the nearest anyway, with reasons.
            return Optional.of(new SuggestionResponse(point,
                    byDistance.stream().limit(SUGGESTION_COUNT).toList(), true));
        }
        return Optional.of(new SuggestionResponse(point, matches, false));
    }

    private ClubSuggestion evaluate(GeocodeResult point, ClubYearView club, String wantedFiliere,
                                     String typeName, List<String> wantedDays,
                                     Map<Long, Set<String>> trainingDays) {
        double distanceKm = haversineKm(point.latitude(), point.longitude(),
                club.getLatitude(), club.getLongitude());
        Double radiusKm = club.getEffectiveRadiusKm();
        // null (not false) when the club has no defined radius — the frontend
        // shows "range not set" rather than a misleading "out of range".
        Boolean withinRange = radiusKm == null ? null : distanceKm <= radiusKm;

        List<String> mismatches = new ArrayList<>();
        if (wantedFiliere != null && !wantedFiliere.equals(club.getFiliere())) {
            boolean typeKnown = "Active for life".equals(club.getFiliere())
                    || "Performance".equals(club.getFiliere());
            mismatches.add(typeKnown
                    ? describeActualType(club.getFiliere()) + ", not " + typeName
                    : "Club type not set");
        }

        List<String> matchedDays = List.of();
        if (!wantedDays.isEmpty()) {
            Set<String> clubDays = trainingDays.getOrDefault(club.getId(), Set.of());
            matchedDays = WEEK.stream()
                    .filter(day -> wantedDays.contains(day) && clubDays.contains(day))
                    .toList();
            if (matchedDays.isEmpty()) {
                if (clubDays.isEmpty()) {
                    mismatches.add("No training schedule on file");
                } else {
                    List<String> trains = WEEK.stream().filter(clubDays::contains).toList();
                    mismatches.add("No training on " + abbreviate(wantedDays, " or ")
                            + " (trains " + abbreviate(trains, ", ") + ")");
                }
            }
        }
        return new ClubSuggestion(club, round1(distanceKm), withinRange, matchedDays, mismatches);
    }

    // The set of weekdays each club has a recurring session on. One bulk read
    // for all clubs rather than a query per club.
    private Map<Long, Set<String>> trainingDaysByClub() {
        Map<Long, Set<String>> byClub = new HashMap<>();
        for (ScheduleSlot slot : scheduleSlotRepository.findAll()) {
            byClub.computeIfAbsent(slot.getClub().getId(), id -> new HashSet<>()).add(slot.getDayOfWeek());
        }
        return byClub;
    }

    private static String filiereForType(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "hobby" -> "Active for life";
            case "competition" -> "Performance";
            default -> null;
        };
    }

    private static String typeName(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "hobby" -> "Hobby";
            case "competition" -> "Competition";
            default -> null;
        };
    }

    private static String describeActualType(String filiere) {
        if ("Active for life".equals(filiere)) {
            return "Hobby club";
        }
        if ("Performance".equals(filiere)) {
            return "Competition club";
        }
        return "No club type set";
    }

    // Keeps only recognised weekday names (case-insensitive), in calendar order.
    private static List<String> normalizeDays(List<String> days) {
        if (days == null || days.isEmpty()) {
            return List.of();
        }
        Set<String> requested = days.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return WEEK.stream().filter(d -> requested.contains(d.toLowerCase(Locale.ROOT))).toList();
    }

    private static String abbreviate(List<String> days, String separator) {
        return days.stream().map(d -> d.substring(0, 3)).collect(Collectors.joining(separator));
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
