package lu.badminton.map.controller;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.Level;
import lu.badminton.map.repository.LevelRepository;
import lu.badminton.map.service.ClubService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/clubs")
public class AdminController {

    private final ClubService clubService;
    private final LevelRepository levelRepository;

    public AdminController(ClubService clubService, LevelRepository levelRepository) {
        this.clubService = clubService;
        this.levelRepository = levelRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clubs", clubService.findAll());
        model.addAttribute("levels", levelRepository.findAll(Sort.by("level")));
        model.addAttribute("clubForm", new ClubForm());
        model.addAttribute("editingId", null);
        return "admin/clubs";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        Club club = clubService.findById(id);
        ClubForm form = new ClubForm();
        form.setName(club.getName());
        form.setCity(club.getCity());
        form.setLatitude(club.getLatitude());
        form.setLongitude(club.getLongitude());
        form.setMembership(club.getMembership());
        form.setClubActivities(club.getClubActivities());
        form.setHumanResources(club.getHumanResources());
        form.setFinance(club.getFinance());
        form.setCommunication(club.getCommunication());
        form.setGovernance(club.getGovernance());
        form.setVisionStrategy(club.getVisionStrategy());
        form.setLevelId(club.getLevel() != null ? club.getLevel().getLevel() : null);
        form.setRadiusOverrideKm(club.getRadiusOverrideKm());
        form.setFiliere(club.getFiliere());

        model.addAttribute("clubs", clubService.findAll());
        model.addAttribute("levels", levelRepository.findAll(Sort.by("level")));
        model.addAttribute("clubForm", form);
        model.addAttribute("editingId", id);
        return "admin/clubs";
    }

    @PostMapping
    public String create(@ModelAttribute ClubForm form) {
        Level level = levelRepository.findById(form.getLevelId()).orElse(null);
        Club club = new Club(
                form.getName(),
                form.getCity(),
                form.getLatitude(),
                form.getLongitude(),
                form.getMembership(),
                form.getClubActivities(),
                form.getHumanResources(),
                form.getFinance(),
                form.getCommunication(),
                form.getGovernance(),
                form.getVisionStrategy(),
                level);
        club.setRadiusOverrideKm(form.getRadiusOverrideKm());
        club.setFiliere(form.getFiliere());
        clubService.save(club);
        return "redirect:/admin/clubs";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @ModelAttribute ClubForm form) {
        Club existing = clubService.findById(id);
        existing.setName(form.getName());
        existing.setCity(form.getCity());
        existing.setLatitude(form.getLatitude());
        existing.setLongitude(form.getLongitude());
        existing.setMembership(form.getMembership());
        existing.setClubActivities(form.getClubActivities());
        existing.setHumanResources(form.getHumanResources());
        existing.setFinance(form.getFinance());
        existing.setCommunication(form.getCommunication());
        existing.setGovernance(form.getGovernance());
        existing.setVisionStrategy(form.getVisionStrategy());
        existing.setLevel(levelRepository.findById(form.getLevelId()).orElse(null));
        existing.setRadiusOverrideKm(form.getRadiusOverrideKm());
        existing.setFiliere(form.getFiliere());
        clubService.save(existing);
        return "redirect:/admin/clubs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clubService.deleteById(id);
        return "redirect:/admin/clubs";
    }
}
