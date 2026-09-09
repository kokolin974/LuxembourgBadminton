package lu.badminton.map.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

// A club's identity and fixed facts only — name, city, and where it sits on
// the map. Everything that can change from year to year (level, the 7
// house-diagram texts, social media, radius override, filière, overlay
// positions) lives on ClubYear instead. A club "exists" in a given year iff
// it has a ClubYear row for that year; there's no separate active flag.
@Entity
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @NotBlank
    private String city;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    // Cascades so deleting a club removes all its yearly snapshots too.
    // Not serialized directly — the API exposes merged ClubYearView objects
    // instead, so echoing the raw list here would just be dead weight.
    @JsonIgnore
    @OneToMany(mappedBy = "club", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClubYear> years = new ArrayList<>();

    protected Club() {
        // JPA
    }

    public Club(String name, String city, double latitude, double longitude) {
        this.name = name;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public List<ClubYear> getYears() {
        return years;
    }
}
