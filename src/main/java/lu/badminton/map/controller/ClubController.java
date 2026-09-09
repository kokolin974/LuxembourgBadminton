package lu.badminton.map.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.ClubYearView;
import lu.badminton.map.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClubController {

    private final ClubService clubService;
    private final ObjectMapper objectMapper;

    public ClubController(ClubService clubService, ObjectMapper objectMapper) {
        this.clubService = clubService;
        this.objectMapper = objectMapper;
    }

    // Every year that has at least one club's data on file — drives the
    // map's year buttons (and defaults to the latest below when omitted).
    @GetMapping("/years")
    public List<Integer> getYears() {
        return clubService.findAllYears();
    }

    // Clubs as they existed in ?year=YYYY (defaults to the latest year on file).
    @GetMapping("/clubs")
    public List<ClubYearView> getClubs(@RequestParam(required = false) Integer year) {
        return clubService.findAllForYear(year);
    }

    // Every club-year snapshot that has ever existed, across all years — the
    // map fetches this once at load to calibrate the heatmap's color scale
    // from the true all-time peak, so a sparse year reads as visibly paler
    // than a well-covered one instead of each year stretching to fill the
    // same range.
    @GetMapping("/clubs/history")
    public List<ClubYearView> getHistory() {
        return clubService.findAllHistory();
    }

    @GetMapping("/clubs/{id}")
    public ResponseEntity<ClubYearView> getClub(@PathVariable Long id, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(clubService.findForYear(id, year));
    }

    // Lightweight endpoint for the map's radius slider (club panel) — updates
    // just the radius override for one club's one year, without needing the
    // full admin form payload.
    @PatchMapping("/clubs/{id}/years/{year}/radius")
    public ResponseEntity<ClubYearView> updateRadius(@PathVariable Long id, @PathVariable int year,
                                                       @RequestBody RadiusUpdate body) {
        ClubYear clubYear = requireYearRow(id, year);
        clubYear.setRadiusOverrideKm(body.radiusOverrideKm());
        return ResponseEntity.ok(ClubYearView.of(clubService.saveYear(clubYear)));
    }

    // Lightweight endpoint for the map's draggable house-diagram text — saves
    // where each level's block was dropped, per club per year, so it's still
    // there next time this club's panel is opened for that year. Body is
    // {"governance": {"left":52.3,"top":30.1}, ...}, stored as-is
    // (serialized) rather than modeled as columns, since it's a UI
    // preference, not business data.
    @PatchMapping("/clubs/{id}/years/{year}/overlay-positions")
    public ResponseEntity<ClubYearView> updateOverlayPositions(@PathVariable Long id, @PathVariable int year,
                                                                 @RequestBody Map<String, Map<String, Double>> positions)
            throws JsonProcessingException {
        ClubYear clubYear = requireYearRow(id, year);
        clubYear.setOverlayPositions(objectMapper.writeValueAsString(positions));
        return ResponseEntity.ok(ClubYearView.of(clubService.saveYear(clubYear)));
    }

    private ClubYear requireYearRow(Long clubId, int year) {
        return clubService.findYearRow(clubId, year)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Club " + clubId + " has no data for year " + year));
    }

    public record RadiusUpdate(Double radiusOverrideKm) {
    }
}
