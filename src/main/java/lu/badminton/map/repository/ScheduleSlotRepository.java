package lu.badminton.map.repository;

import lu.badminton.map.model.ScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    List<ScheduleSlot> findByClubId(Long clubId);
}
