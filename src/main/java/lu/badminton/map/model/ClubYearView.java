package lu.badminton.map.model;

// Read-only, flattened merge of a Club (identity/location) and one of its
// ClubYear snapshots (everything else) — the shape the map and its API
// consumers actually want, so callers don't need to know the data is split
// across two tables. Serialized as-is by Jackson (getters only).
public class ClubYearView {

    private final Long id;
    private final String name;
    private final String city;
    private final double latitude;
    private final double longitude;
    private final int year;
    private final String membership;
    private final String clubActivities;
    private final String humanResources;
    private final String finance;
    private final String communication;
    private final String governance;
    private final String visionStrategy;
    private final Level level;
    private final Double radiusOverrideKm;
    private final Double effectiveRadiusKm;
    private final String filiere;
    private final String overlayPositions;

    private ClubYearView(Club club, ClubYear clubYear) {
        this.id = club.getId();
        this.name = club.getName();
        this.city = club.getCity();
        this.latitude = club.getLatitude();
        this.longitude = club.getLongitude();
        this.year = clubYear.getYear();
        this.membership = clubYear.getMembership();
        this.clubActivities = clubYear.getClubActivities();
        this.humanResources = clubYear.getHumanResources();
        this.finance = clubYear.getFinance();
        this.communication = clubYear.getCommunication();
        this.governance = clubYear.getGovernance();
        this.visionStrategy = clubYear.getVisionStrategy();
        this.level = clubYear.getLevel();
        this.radiusOverrideKm = clubYear.getRadiusOverrideKm();
        this.effectiveRadiusKm = clubYear.getEffectiveRadiusKm();
        this.filiere = clubYear.getFiliere();
        this.overlayPositions = clubYear.getOverlayPositions();
    }

    public static ClubYearView of(ClubYear clubYear) {
        return new ClubYearView(clubYear.getClub(), clubYear);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public int getYear() {
        return year;
    }

    public String getMembership() {
        return membership;
    }

    public String getClubActivities() {
        return clubActivities;
    }

    public String getHumanResources() {
        return humanResources;
    }

    public String getFinance() {
        return finance;
    }

    public String getCommunication() {
        return communication;
    }

    public String getGovernance() {
        return governance;
    }

    public String getVisionStrategy() {
        return visionStrategy;
    }

    public Level getLevel() {
        return level;
    }

    public Double getRadiusOverrideKm() {
        return radiusOverrideKm;
    }

    public Double getEffectiveRadiusKm() {
        return effectiveRadiusKm;
    }

    public String getFiliere() {
        return filiere;
    }

    public String getOverlayPositions() {
        return overlayPositions;
    }
}
