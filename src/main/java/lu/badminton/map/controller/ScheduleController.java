package lu.badminton.map.controller;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.ScheduleSlot;
import lu.badminton.map.repository.ScheduleSlotRepository;
import lu.badminton.map.service.ClubService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/schedule")
public class ScheduleController {

    private final ScheduleSlotRepository scheduleSlotRepository;
    private final ClubService clubService;

    public ScheduleController(ScheduleSlotRepository scheduleSlotRepository, ClubService clubService) {
        this.scheduleSlotRepository = scheduleSlotRepository;
        this.clubService = clubService;
    }

    @GetMapping
    public List<ScheduleSlot> getSchedule(@PathVariable Long clubId) {
        return scheduleSlotRepository.findByClubId(clubId);
    }

    @PostMapping
    public ResponseEntity<ScheduleSlot> createSlot(@PathVariable Long clubId, @RequestBody SlotRequest body) {
        Club club = clubService.findById(clubId);
        ScheduleSlot slot = new ScheduleSlot(club, body.dayOfWeek(),
                LocalTime.parse(body.startTime()), LocalTime.parse(body.endTime()), body.label());
        return ResponseEntity.ok(scheduleSlotRepository.save(slot));
    }

    @PutMapping("/{slotId}")
    public ResponseEntity<ScheduleSlot> updateSlot(@PathVariable Long clubId, @PathVariable Long slotId,
                                                    @RequestBody SlotRequest body) {
        ScheduleSlot slot = scheduleSlotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Schedule slot not found: " + slotId));
        slot.setDayOfWeek(body.dayOfWeek());
        slot.setStartTime(LocalTime.parse(body.startTime()));
        slot.setEndTime(LocalTime.parse(body.endTime()));
        slot.setLabel(body.label());
        return ResponseEntity.ok(scheduleSlotRepository.save(slot));
    }

    @DeleteMapping("/{slotId}")
    public ResponseEntity<Void> deleteSlot(@PathVariable Long clubId, @PathVariable Long slotId) {
        scheduleSlotRepository.deleteById(slotId);
        return ResponseEntity.noContent().build();
    }

    public record SlotRequest(String dayOfWeek, String startTime, String endTime, String label) {
    }
}
