package lu.badminton.map.controller;

import lu.badminton.map.model.Club;
import lu.badminton.map.service.ClubService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/clubs")
public class AdminController {

    private final ClubService clubService;

    public AdminController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clubs", clubService.findAll());
        model.addAttribute("clubForm", new ClubForm());
        return "admin/clubs";
    }

    @PostMapping
    public String create(@ModelAttribute ClubForm form) {
        clubService.save(new Club(
                form.getName(),
                form.getCity(),
                form.getLatitude(),
                form.getLongitude(),
                form.getDescription(),
                form.getWebsite(),
                form.getContactEmail()));
        return "redirect:/admin/clubs";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        clubService.deleteById(id);
        return "redirect:/admin/clubs";
    }
}
