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
        return args -> {
            if (clubRepository.count() > 0) {
                return;
            }
            clubRepository.save(new Club(
                    "Badminton Club Luxembourg", "Luxembourg City",
                    49.6116, 6.1319,
                    "Founded club with youth and adult training groups.",
                    "https://example.lu/bcl", "contact@example.lu"));
            clubRepository.save(new Club(
                    "Badminton Esch", "Esch-sur-Alzette",
                    49.4958, 5.9806,
                    "Club focused on competitive league play.",
                    "https://example.lu/esch", "contact@bc-esch.lu"));
            clubRepository.save(new Club(
                    "Badminton Diekirch", "Diekirch",
                    49.8679, 6.1597,
                    "Recreational club, open training on weekends.",
                    null, "info@bc-diekirch.lu"));
        };
    }
}
