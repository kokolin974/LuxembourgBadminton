package lu.badminton.map.controller;

import lu.badminton.map.model.Club;
import lu.badminton.map.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClub(@PathVariable Long id) {
        clubService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record RadiusUpdate(Double radiusOverrideKm) {
    }
}
