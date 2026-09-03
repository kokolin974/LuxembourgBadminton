package lu.badminton.map.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.LocalTime;

// One recurring weekly training slot for a club, e.g. "Monday 17:00-22:00".
@Entity
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    // "Monday".."Sunday" — plain string rather than an enum, kept consistent
    // with how the rest of this app stores small fixed vocabularies.
    private String dayOfWeek;

    private LocalTime startTime;

    private LocalTime endTime;

    // Optional free text shown on the slot itself (e.g. "Youth", "Coach: X").
    private String label;

    protected ScheduleSlot() {
        // JPA
    }

    public ScheduleSlot(Club club, String dayOfWeek, LocalTime startTime, LocalTime endTime, String label) {
        this.club = club;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    // Not serialized — the frontend already knows which club it asked for
    // (the endpoint is scoped to one club), so echoing the whole parent
    // object back on every slot would just be dead weight in the response.
    @JsonIgnore
    public Club getClub() {
        return club;
    }

    public void setClub(Club club) {
        this.club = club;
    }

    public String getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
