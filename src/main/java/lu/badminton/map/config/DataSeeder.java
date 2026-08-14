package lu.badminton.map.config;

import lu.badminton.map.model.Club;
import lu.badminton.map.repository.ClubRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedClubs(ClubRepository clubRepository) {
        //ajouter rayon
        //deselectioner tous les clubs sauf un
        return args -> {
            if (clubRepository.count() > 0) {
                return;
            }
            // Placeholder entries — one per city from the club location list.
            // Levels are unfilled until real data is imported (planned: CSV import).
            clubRepository.save(placeholderClub("Bettembourg", 49.518611, 6.102778));
            clubRepository.save(placeholderClub("Biwer", 49.702778, 6.374722));
            clubRepository.save(placeholderClub("Dauler / Platen", 49.79321, 5.93514));
            clubRepository.save(placeholderClub("Differdange", 49.520868, 5.892786));
            clubRepository.save(placeholderClub("Dudelange", 49.480556, 6.0875));
            clubRepository.save(placeholderClub("Ettelbruck", 49.8475, 6.104167));
            clubRepository.save(placeholderClub("Hobscheid", 49.688611, 5.914722));
            clubRepository.save(placeholderClub("Itzig", 49.583333, 6.166667));
            clubRepository.save(placeholderClub("Junglinster", 49.707222, 6.253056));
            clubRepository.save(placeholderClub("Kayl", 49.489167, 6.039722));
            clubRepository.save(placeholderClub("Kehlen", 49.668333, 6.035833));
            clubRepository.save(placeholderClub("Kopstal", 49.664444, 6.073056));
            clubRepository.save(placeholderClub("Luxembourg", 49.609819, 6.132684));
            clubRepository.save(placeholderClub("Reckange-sur-Mess", 49.5625, 6.008889));
            clubRepository.save(placeholderClub("Sandweiler", 49.61471, 6.22221));
            clubRepository.save(placeholderClub("Schifflange", 49.506389, 6.012778));
            clubRepository.save(placeholderClub("Schuttrange", 49.620556, 6.268611));
            clubRepository.save(placeholderClub("Walferdange", 49.66321, 6.13224));
            clubRepository.save(placeholderClub("Weiler-la-Tour", 49.540833, 6.200833));
        };
    }

    private static Club placeholderClub(String city, double latitude, double longitude) {
        String tbd = "Not yet added — edit via /admin/clubs.";
        return new Club(
                "Badminton Club " + city, city,
                latitude, longitude,
                tbd, tbd, tbd, tbd, tbd, tbd, tbd);
    }
}
