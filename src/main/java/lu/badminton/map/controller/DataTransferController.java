package lu.badminton.map.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lu.badminton.map.model.DataExport;
import lu.badminton.map.service.DataTransferService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class DataTransferController {

    private final DataTransferService dataTransferService;
    private final ObjectMapper objectMapper;

    public DataTransferController(DataTransferService dataTransferService, ObjectMapper objectMapper) {
        this.dataTransferService = dataTransferService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export() throws Exception {
        DataExport data = dataTransferService.export();
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
        String filename = "badminton-map-export-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    // Full replace — wipes clubs/years/schedules and rebuilds from the file.
    @PostMapping("/import")
    public String importData(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        if (file == null || file.isEmpty()) {
            redirectAttributes.addFlashAttribute("importError", "No file selected.");
            return "redirect:/admin/clubs";
        }
        try {
            DataExport data = objectMapper.readValue(file.getInputStream(), DataExport.class);
            int clubs = dataTransferService.importData(data);
            redirectAttributes.addFlashAttribute("importMessage",
                    "Import complete — " + clubs + " club(s) loaded from " + file.getOriginalFilename() + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("importError", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/clubs";
    }
}
