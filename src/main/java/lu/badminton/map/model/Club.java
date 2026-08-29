package lu.badminton.map.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

// One text field per level of the "club house" diagram (discovery_house_clean.svg),
// from the stone foundation up to the roof.
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

    @Column(length = 2000)
    private String membership;

    @Column(length = 2000)
    private String clubActivities;

    @Column(length = 2000)
    private String humanResources;

    @Column(length = 2000)
    private String finance;

    @Column(length = 2000)
    private String communication;

    @Column(length = 2000)
    private String governance;

    @Column(length = 2000)
    private String visionStrategy;

    @ManyToOne
    @JoinColumn(name = "level_id")
    private Level level;

    // Overrides the level's default radius for this specific club, when set.
    // Adjustable from the map's club panel.
    private Double radiusOverrideKm;

    protected Club() {
        // JPA
    }

    public Club(String name, String city, double latitude, double longitude,
                String membership, String clubActivities, String humanResources,
                String finance, String communication, String governance, String visionStrategy,
                Level level) {
        this.name = name;
        this.city = city;
        this.latitude = latitude;
        this.longitude = longitude;
        this.membership = membership;
        this.clubActivities = clubActivities;
        this.humanResources = humanResources;
        this.finance = finance;
        this.communication = communication;
        this.governance = governance;
        this.visionStrategy = visionStrategy;
        this.level = level;
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

    public String getMembership() {
        return membership;
    }

    public void setMembership(String membership) {
        this.membership = membership;
    }

    public String getClubActivities() {
        return clubActivities;
    }

    public void setClubActivities(String clubActivities) {
        this.clubActivities = clubActivities;
    }

    public String getHumanResources() {
        return humanResources;
    }

    public void setHumanResources(String humanResources) {
        this.humanResources = humanResources;
    }

    public String getFinance() {
        return finance;
    }

    public void setFinance(String finance) {
        this.finance = finance;
    }

    public String getCommunication() {
        return communication;
    }

    public void setCommunication(String communication) {
        this.communication = communication;
    }

    public String getGovernance() {
        return governance;
    }

    public void setGovernance(String governance) {
        this.governance = governance;
    }

    public String getVisionStrategy() {
        return visionStrategy;
    }

    public void setVisionStrategy(String visionStrategy) {
        this.visionStrategy = visionStrategy;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Double getRadiusOverrideKm() {
        return radiusOverrideKm;
    }

    public void setRadiusOverrideKm(Double radiusOverrideKm) {
        this.radiusOverrideKm = radiusOverrideKm;
    }

    // Effective radius for map display: the per-club override if set,
    // otherwise the level's default. Null only if the club has neither.
    public Double getEffectiveRadiusKm() {
        if (radiusOverrideKm != null) {
            return radiusOverrideKm;
        }
        return level != null ? level.getRadiusKm() : null;
    }
}
