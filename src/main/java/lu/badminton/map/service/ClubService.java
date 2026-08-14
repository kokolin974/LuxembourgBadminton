package lu.badminton.map.service;

import lu.badminton.map.model.Club;
import lu.badminton.map.repository.ClubRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClubService {

    private final ClubRepository clubRepository;

    public ClubService(ClubRepository clubRepository) {
        this.clubRepository = clubRepository;
    }

    public List<Club> findAll() {
        return clubRepository.findAll();
    }

    public Club findById(Long id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Club not found: " + id));
    }

    public Club save(Club club) {
        return clubRepository.save(club);
    }

    public void deleteById(Long id) {
        clubRepository.deleteById(id);
    }
}
