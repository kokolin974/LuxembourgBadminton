package lu.badminton.map.controller;

// Plain mutable holder for Thymeleaf form binding — kept separate from the
// Club entity so the entity doesn't need a public no-arg constructor.
public class ClubForm {

    private String name;
    private String city;
    private Double latitude;
    private Double longitude;
    private String membership;
    private String clubActivities;
    private String humanResources;
    private String finance;
    private String communication;
    private String governance;
    private String visionStrategy;
    private Integer levelId;
    private Double radiusOverrideKm;
    private String filiere;

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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
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

    public Integer getLevelId() {
        return levelId;
    }

    public void setLevelId(Integer levelId) {
        this.levelId = levelId;
    }

    public Double getRadiusOverrideKm() {
        return radiusOverrideKm;
    }

    public void setRadiusOverrideKm(Double radiusOverrideKm) {
        this.radiusOverrideKm = radiusOverrideKm;
    }

    public String getFiliere() {
        return filiere;
    }

    public void setFiliere(String filiere) {
        this.filiere = filiere;
    }
}
