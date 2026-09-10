package lu.badminton.map.controller;

import lu.badminton.map.model.SuggestionResponse;
import lu.badminton.map.service.ClubSuggestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/club-suggestions")
public class SuggestionController {

    private final ClubSuggestionService suggestionService;

    public SuggestionController(ClubSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    // Either `address` (free text, geocoded server-side) or `lat`+`lon` (from
    // a picked autocomplete suggestion, used as-is with `label` as the echo
    // text). 404 when a free-text address can't be geocoded — the frontend
    // shows a "couldn't find that address" hint rather than an error.
    @GetMapping
    public ResponseEntity<SuggestionResponse> suggest(@RequestParam(required = false) String address,
                                                       @RequestParam(required = false) Double lat,
                                                       @RequestParam(required = false) Double lon,
                                                       @RequestParam(required = false) String label,
                                                       @RequestParam(required = false) Integer year) {
        return suggestionService.suggest(address, lat, lon, label, year)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
