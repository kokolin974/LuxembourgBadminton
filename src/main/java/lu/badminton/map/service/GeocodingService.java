package lu.badminton.map.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lu.badminton.map.model.GeocodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Turns a free-text address into coordinates via Nominatim (OpenStreetMap's
// geocoder — same data behind the map tiles, no API key). Nominatim's usage
// policy asks callers to identify themselves with a User-Agent and to cache
// results; this is a low-traffic self-hosted app, so an unbounded in-memory
// cache keyed by the normalized query is enough.
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    // Restrict matches to Luxembourg and its neighbours — an address here is
    // almost always in that area, and it keeps ambiguous names (a street that
    // also exists in another country) resolving locally.
    private static final String COUNTRY_CODES = "lu,be,fr,de";

    private final RestClient restClient;
    private final Map<String, Optional<GeocodeResult>> cache = new ConcurrentHashMap<>();

    public GeocodingService(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(6));

        this.restClient = builder
                .requestFactory(requestFactory)
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "badminton-map/0.1 (Luxembourg badminton club finder; self-hosted)")
                .build();
    }

    public Optional<GeocodeResult> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        String key = address.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, k -> lookup(address.trim()));
    }

    private Optional<GeocodeResult> lookup(String address) {
        try {
            NominatimResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", address)
                            .queryParam("format", "jsonv2")
                            .queryParam("limit", 1)
                            .queryParam("countrycodes", COUNTRY_CODES)
                            .build())
                    .retrieve()
                    .body(NominatimResult[].class);

            if (results == null || results.length == 0) {
                return Optional.empty();
            }
            NominatimResult top = results[0];
            return Optional.of(new GeocodeResult(
                    top.displayName(),
                    Double.parseDouble(top.lat()),
                    Double.parseDouble(top.lon())));
        } catch (RuntimeException e) {
            log.warn("Geocoding failed for '{}': {}", address, e.toString());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResult(
            @JsonProperty("display_name") String displayName,
            String lat,
            String lon) {
    }
}
