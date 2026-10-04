package lu.badminton.map.service;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.ClubYearView;
import lu.badminton.map.repository.ClubRepository;
import lu.badminton.map.repository.ClubYearRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubYearRepository clubYearRepository;

    public ClubService(ClubRepository clubRepository, ClubYearRepository clubYearRepository) {
        this.clubRepository = clubRepository;
        this.clubYearRepository = clubYearRepository;
    }

    // --- Years -----------------------------------------------------------

    // Every year that has at least one club's data on file, ascending —
    // drives both the map's year buttons and the admin year picker.
    public List<Integer> findAllYears() {
        return clubYearRepository.findDistinctYears();
    }

    // Most recent year with data, or null if nothing has been entered yet.
    public Integer latestYear() {
        List<Integer> years = findAllYears();
        return years.isEmpty() ? null : years.get(years.size() - 1);
    }

    // --- Merged views (map / API) ----------------------------------------

    // All clubs that existed in the given year, merged with that year's
    // snapshot. Falls back to the latest year on file when year is null.
    public List<ClubYearView> findAllForYear(Integer year) {
        Integer resolved = resolveYear(year);
        if (resolved == null) {
            return List.of();
        }
        return clubYearRepository.findByYear(resolved).stream()
                .map(ClubYearView::of)
                .sorted(Comparator.comparing(ClubYearView::getName))
                .toList();
    }

    // Every club-year row that has ever existed, across all years — used
    // only to calibrate the heatmap's color scale from the true all-time
    // peak (see map.js), never to decide what's shown on the map itself.
    public List<ClubYearView> findAllHistory() {
        return clubYearRepository.findAll().stream()
                .map(ClubYearView::of)
                .toList();
    }

    public ClubYearView findForYear(Long clubId, Integer year) {
        Integer resolved = resolveYear(year);
        if (resolved == null) {
            throw new IllegalArgumentException("No club-year data exists yet");
        }
        ClubYear clubYear = clubYearRepository.findByClubIdAndYear(clubId, resolved)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Club " + clubId + " has no data for year " + resolved));
        return ClubYearView.of(clubYear);
    }

    private Integer resolveYear(Integer year) {
        return year != null ? year : latestYear();
    }

    // --- Raw entities (admin editing) -------------------------------------

    public List<Club> findAllClubs() {
        return clubRepository.findAll();
    }

    public Club findClub(Long id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club not found: " + id));
    }

    public Optional<ClubYear> findYearRow(Long clubId, int year) {
        return clubYearRepository.findByClubIdAndYear(clubId, year);
    }

    // The most recent snapshot on file for a club, regardless of year —
    // used to pre-fill the admin form when adding a year that doesn't exist
    // yet, so only what actually changed needs to be re-typed.
    public Optional<ClubYear> findLatestYearRow(Long clubId) {
        return clubYearRepository.findFirstByClubIdOrderByYearDesc(clubId);
    }

    public List<ClubYear> findAllYearRowsForClub(Long clubId) {
        return clubYearRepository.findByClubIdOrderByYearDesc(clubId);
    }

    public Club createClub(String name, String city, double latitude, double longitude) {
        return clubRepository.save(new Club(name, city, latitude, longitude));
    }

    public void updateClubMasterFields(Club club, String name, String city, double latitude, double longitude) {
        club.setName(name);
        club.setCity(city);
        club.setLatitude(latitude);
        club.setLongitude(longitude);
        clubRepository.save(club);
    }

    public ClubYear saveYear(ClubYear clubYear) {
        return clubYearRepository.save(clubYear);
    }

    // Bulk-creates a snapshot for `toYear` by copying every club's `fromYear`
    // snapshot (all fields). Clubs that already have a `toYear` row are left
    // untouched — this only fills in the ones that are missing. Returns how
    // many rows were created.
    @Transactional
    public int copyYear(int fromYear, int toYear) {
        if (fromYear == toYear) {
            return 0;
        }
        Set<Long> alreadyInTarget = new HashSet<>();
        for (ClubYear existing : clubYearRepository.findByYear(toYear)) {
            alreadyInTarget.add(existing.getClub().getId());
        }

        int created = 0;
        for (ClubYear source : clubYearRepository.findByYear(fromYear)) {
            if (alreadyInTarget.contains(source.getClub().getId())) {
                continue;
            }
            ClubYear copy = new ClubYear(
                    source.getClub(), toYear,
                    source.getMembership(), source.getClubActivities(), source.getHumanResources(),
                    source.getFinance(), source.getGovernance(),
                    source.getVisionStrategy(), source.getLevel());
            copy.setRadiusOverrideKm(source.getRadiusOverrideKm());
            copy.setFiliere(source.getFiliere());
            copy.setOverlayPositions(source.getOverlayPositions());
            copy.setCommunicationEmail(source.getCommunicationEmail());
            copy.setCommunicationWebsite(source.getCommunicationWebsite());
            copy.setCommunicationInstagram(source.getCommunicationInstagram());
            copy.setCommunicationFacebook(source.getCommunicationFacebook());
            clubYearRepository.save(copy);
            created++;
        }
        return created;
    }

    // Removes just this one year's snapshot — if it was the club's only (or
    // last) year, this is effectively "dissolved"; earlier years stay intact.
    // @Transactional because the generated deleteBy... query needs an active
    // transaction to run (plain repository.save/findById don't).
    @Transactional
    public void deleteYear(Long clubId, int year) {
        clubYearRepository.deleteByClubIdAndYear(clubId, year);
    }

    // Removes the year entirely — every club's snapshot for it, across the
    // whole app. Clubs that have no other year on file effectively vanish
    // (their Club identity row stays, just with nothing left to show); the
    // club's other years, if any, are untouched.
    @Transactional
    public void deleteAllForYear(int year) {
        clubYearRepository.deleteByYear(year);
    }

    // Removes the club and every year's snapshot for it (cascades via Club.years).
    public void deleteClub(Long id) {
        clubRepository.deleteById(id);
    }
}
