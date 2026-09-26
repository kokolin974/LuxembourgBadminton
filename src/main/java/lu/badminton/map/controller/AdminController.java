package lu.badminton.map.controller;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.Level;
import lu.badminton.map.repository.LevelRepository;
import lu.badminton.map.service.ClubService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Year;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin/clubs")
public class AdminController {

    private final ClubService clubService;
    private final LevelRepository levelRepository;

    public AdminController(ClubService clubService, LevelRepository levelRepository) {
        this.clubService = clubService;
        this.levelRepository = levelRepository;
    }

    // The year the admin list/forms default to when none is specified: the
    // latest year on file, or the current calendar year if there's no data yet.
    private int defaultYear() {
        Integer latest = clubService.latestYear();
        return latest != null ? latest : Year.now().getValue();
    }

    @GetMapping
    public String list(@RequestParam(required = false) Integer year, Model model) {
        int selectedYear = year != null ? year : defaultYear();
        model.addAttribute("years", clubService.findAllYears());
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("clubs", clubService.findAllForYear(selectedYear));
        model.addAttribute("levels", levelRepository.findAll(Sort.by("level")));
        model.addAttribute("clubForm", blankForm(selectedYear));
        model.addAttribute("editingId", null);
        return "admin/clubs";
    }

    // Blank form for a brand-new club, starting in the given (or default) year.
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Integer year, Model model) {
        int targetYear = year != null ? year : defaultYear();
        model.addAttribute("years", clubService.findAllYears());
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("clubs", clubService.findAllForYear(targetYear));
        model.addAttribute("levels", levelRepository.findAll(Sort.by("level")));
        model.addAttribute("clubForm", blankForm(targetYear));
        model.addAttribute("editingId", null);
        return "admin/clubs";
    }

    // Edit an existing club for a given year. If that year has no snapshot
    // yet, the form pre-fills from the club's most recent prior snapshot —
    // saving then creates the new year rather than overwriting an old one.
    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @RequestParam(required = false) Integer year, Model model) {
        Club club = clubService.findClub(id);
        int targetYear = year != null ? year : defaultYear();

        Optional<ClubYear> existing = clubService.findYearRow(id, targetYear);
        ClubYear source = existing.orElseGet(() -> clubService.findLatestYearRow(id).orElse(null));

        ClubForm form = new ClubForm();
        form.setYear(targetYear);
        form.setName(club.getName());
        form.setCity(club.getCity());
        form.setLatitude(club.getLatitude());
        form.setLongitude(club.getLongitude());
        if (source != null) {
            form.setMembership(source.getMembership());
            form.setClubActivities(source.getClubActivities());
            form.setHumanResources(source.getHumanResources());
            form.setFinance(source.getFinance());
            form.setCommunicationEmail(source.getCommunicationEmail());
            form.setCommunicationWebsite(source.getCommunicationWebsite());
            form.setCommunicationInstagram(source.getCommunicationInstagram());
            form.setCommunicationFacebook(source.getCommunicationFacebook());
            form.setGovernance(source.getGovernance());
            form.setVisionStrategy(source.getVisionStrategy());
            form.setLevelId(source.getLevel() != null ? source.getLevel().getLevel() : null);
            form.setRadiusOverrideKm(source.getRadiusOverrideKm());
            form.setFiliere(source.getFiliere());
        }

        model.addAttribute("years", clubService.findAllYears());
        model.addAttribute("selectedYear", targetYear);
        model.addAttribute("clubs", clubService.findAllForYear(targetYear));
        model.addAttribute("levels", levelRepository.findAll(Sort.by("level")));
        model.addAttribute("clubForm", form);
        model.addAttribute("editingId", id);
        model.addAttribute("isNewYearForClub", existing.isEmpty());
        model.addAttribute("clubYears", clubService.findAllYearRowsForClub(id).stream().map(ClubYear::getYear).toList());
        return "admin/clubs";
    }

    @PostMapping
    public String create(@ModelAttribute ClubForm form) {
        int targetYear = form.getYear() != null ? form.getYear() : defaultYear();
        Club club = clubService.createClub(form.getName(), form.getCity(), form.getLatitude(), form.getLongitude());
        saveYearFromForm(club, targetYear, form);
        return "redirect:/admin/clubs?year=" + targetYear;
    }

    // Creates a year. With `copyFromYear` set, every club's snapshot from that
    // year is copied into the new year (clubs already present in the target
    // year are left as-is). With `startEmpty`, it just lands on that year's
    // (empty) admin page with the "Add a club" form pre-set to it.
    @PostMapping("/years")
    public String createYear(@RequestParam int year,
                              @RequestParam(required = false) Integer copyFromYear,
                              @RequestParam(required = false) Boolean startEmpty) {
        if (year < 2000 || year > 2100) {
            return "redirect:/admin/clubs";
        }
        if (copyFromYear != null && !Boolean.TRUE.equals(startEmpty)) {
            clubService.copyYear(copyFromYear, year);
        }
        return "redirect:/admin/clubs?year=" + year;
    }

    @PostMapping("/{id}/years/{year}/update")
    public String update(@PathVariable Long id, @PathVariable int year, @ModelAttribute ClubForm form) {
        Club club = clubService.findClub(id);
        clubService.updateClubMasterFields(club, form.getName(), form.getCity(), form.getLatitude(), form.getLongitude());
        saveYearFromForm(club, year, form);
        return "redirect:/admin/clubs?year=" + year;
    }

    private void saveYearFromForm(Club club, int year, ClubForm form) {
        // levelId is null when "— No level —" is picked in the form.
        Level level = form.getLevelId() != null
                ? levelRepository.findById(form.getLevelId()).orElse(null)
                : null;
        ClubYear clubYear = clubService.findYearRow(club.getId(), year).orElseGet(() ->
                new ClubYear(club, year, null, null, null, null, null, null, null));
        clubYear.setClub(club);
        clubYear.setYear(year);
        clubYear.setMembership(form.getMembership());
        clubYear.setClubActivities(form.getClubActivities());
        clubYear.setHumanResources(form.getHumanResources());
        clubYear.setFinance(form.getFinance());
        clubYear.setCommunicationEmail(form.getCommunicationEmail());
        clubYear.setCommunicationWebsite(form.getCommunicationWebsite());
        clubYear.setCommunicationInstagram(form.getCommunicationInstagram());
        clubYear.setCommunicationFacebook(form.getCommunicationFacebook());
        clubYear.setGovernance(form.getGovernance());
        clubYear.setVisionStrategy(form.getVisionStrategy());
        clubYear.setLevel(level);
        clubYear.setRadiusOverrideKm(form.getRadiusOverrideKm());
        clubYear.setFiliere(form.getFiliere());
        clubService.saveYear(clubYear);
    }

    // Removes just this club's snapshot for one year (dissolves the club if
    // it was its only/last year) — earlier years are untouched.
    @PostMapping("/{id}/years/{year}/delete")
    public String deleteYear(@PathVariable Long id, @PathVariable int year) {
        clubService.deleteYear(id, year);
        return "redirect:/admin/clubs?year=" + year;
    }

    // Removes the club entirely, every year included.
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) Integer year) {
        clubService.deleteClub(id);
        return "redirect:/admin/clubs" + (year != null ? "?year=" + year : "");
    }

    private ClubForm blankForm(int year) {
        ClubForm form = new ClubForm();
        form.setYear(year);
        return form;
    }
}
