package lu.badminton.map.config;

import lu.badminton.map.model.Club;
import lu.badminton.map.model.Level;
import lu.badminton.map.repository.ClubRepository;
import lu.badminton.map.repository.LevelRepository;
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
import java.util.ArrayList;
import java.util.List;

@Configuration
public class DataSeeder {

    private static final String CLUBS_CSV_PATH = "import/clubs_import.csv";

    @Bean
    CommandLineRunner seedData(ClubRepository clubRepository, LevelRepository levelRepository) {
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

            if (clubRepository.count() > 0) {
                return;
            }
            importClubsFromCsv(clubRepository, levelRepository);
        };
    }

    // Reads import/clubs_import.csv (one row per real-world data point, grouped
    // by house level in the column names — see the file itself for the
    // "N_fieldName" convention) and composes the actual display text for each
    // of the 7 house-level bands. This composition step is deliberately here,
    // not baked into the CSV, so the CSV stays a clean structured source and
    // this is the one place that turns it into prose.
    private static void importClubsFromCsv(ClubRepository clubRepository, LevelRepository levelRepository) throws IOException {
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
                        Double.parseDouble(record.get("longitude")),
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
                club.setFiliere(blankToNull(record.get("6_filiere")));
                clubRepository.save(club);
            }
        }
    }

    private static String membershipText(String licencesJeunes, String licencesTotal) {
        return licencesJeunes + " youth licences, " + licencesTotal + " licences total.";
    }

    private static String clubActivitiesText(String jeuneTeams, String seniorTeams, String cadresTotal) {
        return jeuneTeams + " youth interclub team(s), " + seniorTeams + " senior interclub team(s), "
                + cadresTotal + " total cadre members.";
    }

    private static String humanResourcesText(String responsable, String entraineur, String officiel) {
        StringBuilder text = new StringBuilder("Manager: ").append(responsable).append(".");
        if (!isBlank(entraineur)) {
            text.append(" Coach(es): ").append(entraineur).append(".");
        }
        if (!isBlank(officiel)) {
            text.append(" Technical officials: ").append(officiel).append(".");
        }
        return text.toString();
    }

    private static String financeText(String jeune, String adulte, String hobby) {
        if (isBlank(jeune) && isBlank(adulte) && isBlank(hobby)) {
            return "Membership fees not published.";
        }
        return "Youth: €" + jeune + ", Adult: €" + adulte + ", Hobby: €" + hobby + ".";
    }

    private static String communicationText(boolean email, boolean website, boolean instagram, boolean facebook) {
        List<String> present = new ArrayList<>();
        if (email) present.add("email");
        if (website) present.add("website");
        if (instagram) present.add("Instagram");
        if (facebook) present.add("Facebook");
        if (present.isEmpty()) {
            return "No contact channels on file.";
        }

        StringBuilder text = new StringBuilder("Reachable by ").append(String.join(", ", present)).append(".");
        List<String> missing = new ArrayList<>();
        if (!website) missing.add("website");
        if (!instagram) missing.add("Instagram");
        if (!facebook) missing.add("Facebook");
        if (!missing.isEmpty()) {
            text.append(" No ").append(joinWithOr(missing)).append(".");
        }
        return text.toString();
    }

    private static String joinWithOr(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " or " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + ", or " + items.get(items.size() - 1);
    }

    private static String governanceText(String levelOld, String filiere, String levelNew) {
        if (isBlank(levelNew)) {
            return "Classification not yet assigned.";
        }
        StringBuilder text = new StringBuilder("Classified as ").append(levelNew);
        if (!isBlank(filiere)) {
            text.append(" (").append(filiere).append(" pathway)");
        }
        text.append(".");
        if (!isBlank(levelOld)) {
            text.append(levelOld.equals("Candidat")
                    ? " Previously a candidate club (no official level)."
                    : " Previously " + levelOld + ".");
        }
        return text.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }
}
