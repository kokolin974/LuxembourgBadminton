package lu.badminton.map.repository;

import lu.badminton.map.model.Level;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, Integer> {
}
