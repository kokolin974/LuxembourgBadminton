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
            clubRepository.save(new Club(
                    "Badminton Club Luxembourg", "Luxembourg City",
                    49.6116, 6.1319,
                    "Open to residents of Luxembourg City and surroundings, all ages welcome.",
                    "Youth training Wed/Fri evenings, adult league matches on Saturdays.",
                    "3 volunteer coaches, committee of 5 elected annually.",
                    "Funded by membership fees and a yearly municipal grant.",
                    "Newsletter monthly, active on Instagram. Contact: contact@example.lu, https://example.lu/bcl",
                    "Board meets monthly; general assembly each September.",
                    "Grow youth membership and field a team in the national league by 2028."));
            clubRepository.save(new Club(
                    "Badminton Esch", "Esch-sur-Alzette",
                    49.4958, 5.9806,
                    "Competitive-focused club, tryout required for senior teams.",
                    "Club focused on competitive league play, training 4x/week.",
                    "2 certified coaches, physio support during tournament season.",
                    "Sponsored by local businesses, plus membership dues.",
                    "Contact: contact@bc-esch.lu, https://example.lu/esch",
                    "Governed by an elected board of 7.",
                    "Compete at the top of the national league within 5 years."));
            clubRepository.save(new Club(
                    "Badminton Diekirch", "Diekirch",
                    49.8679, 6.1597,
                    "Casual, family-friendly membership, no tryouts.",
                    "Recreational club, open training on weekends.",
                    "Run entirely by volunteers.",
                    "Low membership fee, no major sponsors.",
                    "Contact: info@bc-diekirch.lu",
                    "Informal committee of founding members.",
                    "Stay a welcoming, low-pressure club for the local community."));
        };
    }
}
