package lu.badminton.map.repository;

import lu.badminton.map.model.ClubYear;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ClubYearRepository extends JpaRepository<ClubYear, Long> {

    List<ClubYear> findByYear(int year);

    List<ClubYear> findByClubIdOrderByYearDesc(Long clubId);

    Optional<ClubYear> findByClubIdAndYear(Long clubId, int year);

    Optional<ClubYear> findFirstByClubIdOrderByYearDesc(Long clubId);

    void deleteByClubIdAndYear(Long clubId, int year);

    @Query("select distinct cy.year from ClubYear cy order by cy.year")
    List<Integer> findDistinctYears();
}
