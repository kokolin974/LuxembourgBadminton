package lu.badminton.map.controller;

import lu.badminton.map.model.AddressSuggestion;
import lu.badminton.map.service.AddressAutocompleteService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/address-autocomplete")
public class AutocompleteController {

    private final AddressAutocompleteService autocompleteService;

    public AutocompleteController(AddressAutocompleteService autocompleteService) {
        this.autocompleteService = autocompleteService;
    }

    // Always 200 — an empty list on no match or an upstream hiccup, so the
    // typeahead just shows nothing rather than surfacing an error.
    @GetMapping
    public List<AddressSuggestion> suggest(@RequestParam String q) {
        return autocompleteService.suggest(q);
    }
}
