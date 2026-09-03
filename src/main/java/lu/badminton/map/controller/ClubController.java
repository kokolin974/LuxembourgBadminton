package lu.badminton.map.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lu.badminton.map.model.Club;
import lu.badminton.map.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;
    private final ObjectMapper objectMapper;

    public ClubController(ClubService clubService, ObjectMapper objectMapper) {
        this.clubService = clubService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<Club> getAllClubs() {
        return clubService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Club> getClub(@PathVariable Long id) {
        return ResponseEntity.ok(clubService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Club> createClub(@RequestBody Club club) {
        return ResponseEntity.ok(clubService.save(club));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Club> updateClub(@PathVariable Long id, @RequestBody Club updated) {
        Club existing = clubService.findById(id);
        existing.setName(updated.getName());
        existing.setCity(updated.getCity());
        existing.setLatitude(updated.getLatitude());
        existing.setLongitude(updated.getLongitude());
        existing.setMembership(updated.getMembership());
        existing.setClubActivities(updated.getClubActivities());
        existing.setHumanResources(updated.getHumanResources());
        existing.setFinance(updated.getFinance());
        existing.setCommunication(updated.getCommunication());
        existing.setGovernance(updated.getGovernance());
        existing.setVisionStrategy(updated.getVisionStrategy());
        existing.setLevel(updated.getLevel());
        existing.setFiliere(updated.getFiliere());
        return ResponseEntity.ok(clubService.save(existing));
    }

    // Lightweight endpoint for the map's radius slider (club panel) — updates
    // just the radius override without needing the full admin form payload.
    @PatchMapping("/{id}/radius")
    public ResponseEntity<Club> updateRadius(@PathVariable Long id, @RequestBody RadiusUpdate body) {
        Club existing = clubService.findById(id);
        existing.setRadiusOverrideKm(body.radiusOverrideKm());
        return ResponseEntity.ok(clubService.save(existing));
    }

    // Lightweight endpoint for the map's draggable house-diagram text — saves
    // where each level's block was dropped, per club, so it's still there
    // next time this club's panel is opened. Body is {"governance":
    // {"left":52.3,"top":30.1}, ...}, stored as-is (serialized) rather than
    // modeled as columns, since it's a UI preference, not business data.
    @PatchMapping("/{id}/overlay-positions")
    public ResponseEntity<Club> updateOverlayPositions(@PathVariable Long id,
                                                        @RequestBody Map<String, Map<String, Double>> positions)
            throws JsonProcessingException {
        Club existing = clubService.findById(id);
        existing.setOverlayPositions(objectMapper.writeValueAsString(positions));
        return ResponseEntity.ok(clubService.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable Long id) {
        clubService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record RadiusUpdate(Double radiusOverrideKm) {
    }
}
