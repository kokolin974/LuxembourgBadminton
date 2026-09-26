package lu.badminton.map.service;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.DataExport;
import lu.badminton.map.model.Level;
import lu.badminton.map.model.ScheduleSlot;
import lu.badminton.map.repository.ClubRepository;
import lu.badminton.map.repository.ClubYearRepository;
import lu.badminton.map.repository.LevelRepository;
import lu.badminton.map.repository.ScheduleSlotRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Whole-database export/import as one JSON document (see DataExport). Import
// is a full replace: clubs, year snapshots and schedules are wiped and
// rebuilt from the file; levels are updated in place by number so nothing
// that could still reference them is deleted.
@Service
public class DataTransferService {

    private final LevelRepository levelRepository;
    private final ClubRepository clubRepository;
    private final ClubYearRepository clubYearRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;

    public DataTransferService(LevelRepository levelRepository, ClubRepository clubRepository,
                                ClubYearRepository clubYearRepository,
                                ScheduleSlotRepository scheduleSlotRepository) {
        this.levelRepository = levelRepository;
        this.clubRepository = clubRepository;
        this.clubYearRepository = clubYearRepository;
        this.scheduleSlotRepository = scheduleSlotRepository;
    }

    @Transactional(readOnly = true)
    public DataExport export() {
        List<DataExport.LevelExport> levels = levelRepository.findAll(Sort.by("level")).stream()
                .map(level -> new DataExport.LevelExport(level.getLevel(), level.getLabel(), level.getRadiusKm()))
                .toList();

        List<DataExport.ClubExport> clubs = clubRepository.findAll().stream()
                .sorted(Comparator.comparing(Club::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toClubExport)
                .toList();

        return new DataExport(Instant.now().toString(), DataExport.CURRENT_VERSION, levels, clubs);
    }

    private DataExport.ClubExport toClubExport(Club club) {
        List<DataExport.ClubYearExport> years = club.getYears().stream()
                .sorted(Comparator.comparingInt(ClubYear::getYear))
                .map(clubYear -> new DataExport.ClubYearExport(
                        clubYear.getYear(),
                        clubYear.getLevel() != null ? clubYear.getLevel().getLevel() : null,
                        clubYear.getRadiusOverrideKm(),
                        clubYear.getFiliere(),
                        clubYear.getMembership(),
                        clubYear.getClubActivities(),
                        clubYear.getHumanResources(),
                        clubYear.getFinance(),
                        clubYear.getCommunicationEmail(),
                        clubYear.getCommunicationWebsite(),
                        clubYear.getCommunicationInstagram(),
                        clubYear.getCommunicationFacebook(),
                        clubYear.getGovernance(),
                        clubYear.getVisionStrategy(),
                        clubYear.getOverlayPositions()))
                .toList();

        List<DataExport.ScheduleExport> schedule = scheduleSlotRepository.findByClubId(club.getId()).stream()
                .sorted(Comparator.comparing(ScheduleSlot::getDayOfWeek).thenComparing(ScheduleSlot::getStartTime))
                .map(slot -> new DataExport.ScheduleExport(
                        slot.getDayOfWeek(),
                        slot.getStartTime().toString(),
                        slot.getEndTime().toString(),
                        slot.getLabel()))
                .toList();

        return new DataExport.ClubExport(club.getName(), club.getCity(),
                club.getLatitude(), club.getLongitude(), years, schedule);
    }

    // Returns the number of clubs imported.
    @Transactional
    public int importData(DataExport data) {
        if (data == null || data.clubs() == null) {
            throw new IllegalArgumentException("File has no 'clubs' array — is it a Badminton Map export?");
        }

        // Wipe in FK-safe order (deleteAllInBatch skips JPA cascades).
        scheduleSlotRepository.deleteAllInBatch();
        clubYearRepository.deleteAllInBatch();
        clubRepository.deleteAllInBatch();

        Map<Integer, Level> levelsByNumber = new HashMap<>();
        for (Level level : levelRepository.findAll()) {
            levelsByNumber.put(level.getLevel(), level);
        }
        if (data.levels() != null) {
            for (DataExport.LevelExport levelExport : data.levels()) {
                Level level = levelsByNumber.get(levelExport.level());
                if (level == null) {
                    level = new Level(levelExport.level(), levelExport.label(), levelExport.radiusKm());
                } else {
                    level.setLabel(levelExport.label());
                    level.setRadiusKm(levelExport.radiusKm());
                }
                levelsByNumber.put(levelExport.level(), levelRepository.save(level));
            }
        }

        for (DataExport.ClubExport clubExport : data.clubs()) {
            Club club = clubRepository.save(new Club(
                    clubExport.name(), clubExport.city(),
                    clubExport.latitude(), clubExport.longitude()));

            if (clubExport.years() != null) {
                for (DataExport.ClubYearExport yearExport : clubExport.years()) {
                    Level level = yearExport.level() != null ? levelsByNumber.get(yearExport.level()) : null;
                    ClubYear clubYear = new ClubYear(club, yearExport.year(),
                            yearExport.membership(), yearExport.clubActivities(), yearExport.humanResources(),
                            yearExport.finance(), yearExport.governance(),
                            yearExport.visionStrategy(), level);
                    clubYear.setRadiusOverrideKm(yearExport.radiusOverrideKm());
                    clubYear.setFiliere(yearExport.filiere());
                    clubYear.setOverlayPositions(yearExport.overlayPositions());
                    clubYear.setCommunicationEmail(yearExport.communicationEmail());
                    clubYear.setCommunicationWebsite(yearExport.communicationWebsite());
                    clubYear.setCommunicationInstagram(yearExport.communicationInstagram());
                    clubYear.setCommunicationFacebook(yearExport.communicationFacebook());
                    clubYearRepository.save(clubYear);
                }
            }

            if (clubExport.schedule() != null) {
                for (DataExport.ScheduleExport scheduleExport : clubExport.schedule()) {
                    scheduleSlotRepository.save(new ScheduleSlot(club,
                            scheduleExport.dayOfWeek(),
                            LocalTime.parse(scheduleExport.startTime()),
                            LocalTime.parse(scheduleExport.endTime()),
                            scheduleExport.label()));
                }
            }
        }

        return data.clubs().size();
    }
}
