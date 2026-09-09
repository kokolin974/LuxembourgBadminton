package lu.badminton.map.config;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.ClubYear;
import lu.badminton.map.model.Level;
import lu.badminton.map.model.ScheduleSlot;
import lu.badminton.map.repository.ClubRepository;
import lu.badminton.map.repository.ClubYearRepository;
import lu.badminton.map.repository.LevelRepository;
import lu.badminton.map.repository.ScheduleSlotRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class DataSeeder {

    private static final String CLUBS_CSV_PATH = "import/clubs_import.csv";
    private static final String SCHEDULES_CSV_PATH = "import/schedules_import.csv";

    // The CSV has no year column — it's a single snapshot, so it's imported
    // as this one year. Later years are entered through the admin page.
    private static final int SEED_YEAR = 2026;

    @Bean
    CommandLineRunner seedData(ClubRepository clubRepository, ClubYearRepository clubYearRepository,
                                LevelRepository levelRepository, ScheduleSlotRepository scheduleSlotRepository) {
        return args -> {
            if (levelRepository.count() == 0) {
                levelRepository.save(new Level(1, "Découverte", 5));
                levelRepository.save(new Level(2, "Initiation", 10));
                levelRepository.save(new Level(3, "Animation", 15));
                levelRepository.save(new Level(4, "Bronze", 10));
                levelRepository.save(new Level(5, "Argent", 15));
                levelRepository.save(new Level(6, "Or", 22));
                levelRepository.save(new Level(7, "Platine", 30));
            }

            if (clubRepository.count() == 0) {
                importClubsFromCsv(clubRepository, clubYearRepository, levelRepository);
            }

            if (scheduleSlotRepository.count() == 0) {
                importScheduleFromCsv(clubRepository, scheduleSlotRepository);
            }
        };
    }

    // Reads import/clubs_import.csv (one row per real-world data point, grouped
    // by house level in the column names — see the file itself for the
    // "N_fieldName" convention) and composes the actual display text for each
    // of the 7 house-level bands. This composition step is deliberately here,
    // not baked into the CSV, so the CSV stays a clean structured source and
    // this is the one place that turns it into prose. Each row becomes a Club
    // (identity/location) plus one ClubYear snapshot for SEED_YEAR.
    private static void importClubsFromCsv(ClubRepository clubRepository, ClubYearRepository clubYearRepository,
                                             LevelRepository levelRepository) throws IOException {
        ClassPathResource resource = new ClassPathResource(CLUBS_CSV_PATH);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                String levelStr = record.get("level");
                Level level = levelStr.isBlank() ? null : levelRepository.findById(Integer.parseInt(levelStr)).orElse(null);

                Club club = new Club(
                        record.get("name"),
                        record.get("city"),
                        Double.parseDouble(record.get("latitude")),
                        Double.parseDouble(record.get("longitude")));
                clubRepository.save(club);

                ClubYear clubYear = new ClubYear(
                        club,
                        SEED_YEAR,
                        membershipText(record.get("1_licencesJeunes"), record.get("1_licencesTotal")),
                        clubActivitiesText(record.get("2_equipeInterclubJeune"), record.get("2_equipeInterclubSenior"), record.get("2_cadresTotal")),
                        humanResourcesText(record.get("3_nomResponsable"), record.get("3_entraineurProClub"), record.get("3_officielTechnique")),
                        financeText(record.get("4_cotisationJeune"), record.get("4_cotisationAdulte"), record.get("4_cotisationHobby")),
                        communicationText(
                                Boolean.parseBoolean(record.get("5_hasEmail")),
                                Boolean.parseBoolean(record.get("5_hasWebsite")),
                                Boolean.parseBoolean(record.get("5_hasInstagram")),
                                Boolean.parseBoolean(record.get("5_hasFacebook"))),
                        governanceText(record.get("6_levelOld"), record.get("6_filiere"), record.get("6_levelNew")),
                        null, // Vision & Strategy — no source data yet
                        level);
                clubYear.setFiliere(blankToNull(record.get("6_filiere")));
                clubYearRepository.save(clubYear);
            }
        }
    }

    // Reads import/schedules_import.csv (clubName, dayOfWeek, startTime,
    // endTime — one row per recurring weekly session) and links each row to
    // the matching Club by exact name match against what importClubsFromCsv
    // already created.
    private static void importScheduleFromCsv(ClubRepository clubRepository,
                                                ScheduleSlotRepository scheduleSlotRepository) throws IOException {
        ClassPathResource resource = new ClassPathResource(SCHEDULES_CSV_PATH);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();

        Map<String, Club> clubsByName = clubRepository.findAll().stream()
                .collect(Collectors.toMap(Club::getName, club -> club));

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                Club club = clubsByName.get(record.get("clubName"));
                if (club == null) {
                    continue;
                }
                ScheduleSlot slot = new ScheduleSlot(
                        club,
                        record.get("dayOfWeek"),
                        LocalTime.parse(record.get("startTime")),
                        LocalTime.parse(record.get("endTime")),
                        null); // no label data in the source schedule tables
                scheduleSlotRepository.save(slot);
            }
        }
    }

    // Each of these returns one fact per line (newline-separated) rather than
    // a single run-on sentence — the map panel renders multi-line text as a
    // bullet list, and a plain textarea already edits newlines fine from the
    // admin page, so this is the one shared format both use.

    private static String membershipText(String licencesJeunes, String licencesTotal) {
        return licencesJeunes + " youth licences\n" + licencesTotal + " licences total";
    }

    private static String clubActivitiesText(String jeuneTeams, String seniorTeams, String cadresTotal) {
        return jeuneTeams + " youth interclub team(s)\n" + seniorTeams + " senior interclub team(s)\n"
                + cadresTotal + " total cadre members";
    }

    private static String humanResourcesText(String responsable, String entraineur, String officiel) {
        List<String> lines = new ArrayList<>();
        lines.add("Manager: " + responsable);
        if (!isBlank(entraineur)) {
            lines.add("Coach(es): " + entraineur);
        }
        if (!isBlank(officiel)) {
            lines.add("Technical officials: " + officiel);
        }
        return String.join("\n", lines);
    }

    private static String financeText(String jeune, String adulte, String hobby) {
        if (isBlank(jeune) && isBlank(adulte) && isBlank(hobby)) {
            return "Membership fees not published";
        }
        return "Youth: €" + jeune + "\nAdult: €" + adulte + "\nHobby: €" + hobby;
    }

    private static String communicationText(boolean email, boolean website, boolean instagram, boolean facebook) {
        List<String> present = new ArrayList<>();
        if (email) present.add("email");
        if (website) present.add("website");
        if (instagram) present.add("Instagram");
        if (facebook) present.add("Facebook");
        if (present.isEmpty()) {
            return "No contact channels on file";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Available: " + String.join(", ", present));
        List<String> missing = new ArrayList<>();
        if (!website) missing.add("website");
        if (!instagram) missing.add("Instagram");
        if (!facebook) missing.add("Facebook");
        if (!missing.isEmpty()) {
            lines.add("Not available: " + String.join(", ", missing));
        }
        return String.join("\n", lines);
    }

    private static String governanceText(String levelOld, String filiere, String levelNew) {
        if (isBlank(levelNew)) {
            return "Classification not yet assigned";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Classified as " + levelNew + (isBlank(filiere) ? "" : " (" + filiere + " pathway)"));
        if (!isBlank(levelOld)) {
            lines.add(levelOld.equals("Candidat")
                    ? "Previously a candidate club (no official level)"
                    : "Previously " + levelOld);
        }
        return String.join("\n", lines);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
