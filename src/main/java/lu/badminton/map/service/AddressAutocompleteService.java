package lu.badminton.map.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lu.badminton.map.model.AddressSuggestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Address typeahead via Photon (photon.komoot.io) — a free, keyless,
// OSM-based geocoder built for autocomplete (unlike Nominatim, whose usage
// policy forbids per-keystroke querying). Each result carries coordinates,
// so picking a suggestion skips the geocoding step entirely.
@Service
public class AddressAutocompleteService {

    private static final Logger log = LoggerFactory.getLogger(AddressAutocompleteService.class);

    private static final int MAX_RESULTS = 5;

    // Bias ranking toward Luxembourg's centre, and hard-limit results to a
    // box around it (covers the country plus a generous margin of the
    // neighbouring FR / BE / DE border regions).
    private static final double BIAS_LAT = 49.81;
    private static final double BIAS_LON = 6.13;
    private static final String BBOX = "4.5,48.5,7.6,50.6";

    private final RestClient restClient;
    private final Map<String, List<AddressSuggestion>> cache = new ConcurrentHashMap<>();

    public AddressAutocompleteService(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl("https://photon.komoot.io")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "badminton-map/0.1 (Luxembourg badminton club finder; self-hosted)")
                .build();
    }

    public List<AddressSuggestion> suggest(String query) {
        if (query == null || query.isBlank() || query.trim().length() < 3) {
            return List.of();
        }
        String key = query.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, k -> lookup(query.trim()));
    }

    private List<AddressSuggestion> lookup(String query) {
        try {
            PhotonResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/")
                            .queryParam("q", query)
                            .queryParam("limit", MAX_RESULTS)
                            .queryParam("lang", "fr")
                            .queryParam("lat", BIAS_LAT)
                            .queryParam("lon", BIAS_LON)
                            .queryParam("bbox", BBOX)
                            .build())
                    .retrieve()
                    .body(PhotonResponse.class);

            if (response == null || response.features() == null) {
                return List.of();
            }

            List<AddressSuggestion> suggestions = new ArrayList<>();
            Set<String> seenLabels = new HashSet<>();
            for (PhotonFeature feature : response.features()) {
                if (feature.geometry() == null || feature.geometry().coordinates() == null
                        || feature.geometry().coordinates().size() < 2) {
                    continue;
                }
                double lon = feature.geometry().coordinates().get(0);
                double lat = feature.geometry().coordinates().get(1);
                String label = composeLabel(feature.properties());
                // Photon often returns several segments of the same street as
                // separate features — collapse to the first of each label.
                if (!label.isBlank() && seenLabels.add(label.toLowerCase(Locale.ROOT))) {
                    suggestions.add(new AddressSuggestion(label, lat, lon));
                }
            }
            return List.copyOf(suggestions);
        } catch (RuntimeException e) {
            log.warn("Address autocomplete failed for '{}': {}", query, e.toString());
            return List.of();
        }
    }

    // Photon returns structured address parts, not a single display string —
    // stitch a readable one: "<house> <street>" (or the POI name), then the
    // locality, then the country.
    private static String composeLabel(PhotonProperties p) {
        if (p == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();

        String primary;
        if (isPresent(p.street())) {
            primary = isPresent(p.housenumber()) ? p.housenumber() + " " + p.street() : p.street();
        } else {
            primary = p.name();
        }
        addIfPresent(parts, primary);

        String locality = joinNonBlank(" ", p.postcode(), p.city());
        if (isPresent(locality) && !locality.equalsIgnoreCase(primary)) {
            parts.add(locality);
        }

        addIfPresent(parts, p.country());
        return String.join(", ", parts);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (isPresent(value)) {
            parts.add(value.trim());
        }
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> present = new ArrayList<>();
        for (String value : values) {
            if (isPresent(value)) {
                present.add(value.trim());
            }
        }
        return String.join(separator, present);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonResponse(List<PhotonFeature> features) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonFeature(PhotonGeometry geometry, PhotonProperties properties) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonGeometry(List<Double> coordinates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PhotonProperties(String name, String street, String housenumber,
                                     String postcode, String city, String country) {
    }
}
