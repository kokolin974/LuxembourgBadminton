package lu.badminton.map.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// Club skill/size tier (Découverte..Platine), each with its own catchment
// radius. The level number itself is the primary key — this is a small,
// fixed reference table, not an auto-generated one.
@Entity
public class Level {

    @Id
    private Integer level;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private double radiusKm;

    protected Level() {
        // JPA
    }

    public Level(Integer level, String label, double radiusKm) {
        this.level = level;
        this.label = label;
        this.radiusKm = radiusKm;
    }

    public Integer getLevel() {
        return level;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }
}
