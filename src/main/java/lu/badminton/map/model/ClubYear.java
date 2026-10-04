package lu.badminton.map.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// One year's snapshot of a club's data — the 7 house-diagram texts, level,
// radius override, filière, and overlay positions, all as they were for that
// specific year. A club's history is the set of these rows across years;
// its first/last year with a row is effectively its creation/dissolution.
@Entity
@Table(name = "club_year", uniqueConstraints = @UniqueConstraint(columnNames = {"club_id", "club_year_num"}))
public class ClubYear {

    // Filière value for clubs not affiliated with the federation. They get
    // their own marker icon on the map and are left out of the "Find a club"
    // suggestions. Kept in sync by hand with the admin dropdown option and
    // NON_AFFILIE_FILIERE in map.js.
    public static final String FILIERE_NON_AFFILIE = "Non affilié";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    // Named "club_year_num" rather than "year" — "year" is a reserved word
    // in H2's SQL grammar and breaks generated DDL/DML.
    @Column(name = "club_year_num", nullable = false)
    private int year;

    @Column(length = 2000)
    private String membership;

    @Column(length = 2000)
    private String clubActivities;

    @Column(length = 2000)
    private String humanResources;

    @Column(length = 2000)
    private String finance;

    // Communication is 4 distinct optional links rather than one text blob
    // (unlike the other house levels) — each one needs to render as its own
    // clickable link on the map, not just be read as prose. Email is stored
    // bare (no "mailto:") so it displays the same way admin-side as the
    // others; the map builds the mailto: link when rendering.
    private String communicationEmail;

    private String communicationWebsite;

    private String communicationInstagram;

    private String communicationFacebook;

    @Column(length = 2000)
    private String governance;

    @Column(length = 2000)
    private String visionStrategy;

    @ManyToOne
    @JoinColumn(name = "level_id")
    private Level level;

    // Overrides the level's default radius for this club in this year.
    // Adjustable from the map's club panel.
    private Double radiusOverrideKm;

    // Performance / Active for life. Plain picklist attribute — no computed
    // behavior depends on it, unlike level (which drives icon/radius).
    private String filiere;

    // Per-level {left, top} percentage offsets for the house diagram's
    // draggable text overlays, serialized as JSON, same shape as it always
    // was on Club — just year-scoped now, since a club's content (and so a
    // sensible layout for it) can differ from year to year.
    @Column(length = 2000)
    private String overlayPositions;

    protected ClubYear() {
        // JPA
    }

    public ClubYear(Club club, int year, String membership, String clubActivities, String humanResources,
                     String finance, String governance, String visionStrategy, Level level) {
        this.club = club;
        this.year = year;
        this.membership = membership;
        this.clubActivities = clubActivities;
        this.humanResources = humanResources;
        this.finance = finance;
        this.governance = governance;
        this.visionStrategy = visionStrategy;
        this.level = level;
    }

    public Long getId() {
        return id;
    }

    public Club getClub() {
        return club;
    }

    public void setClub(Club club) {
        this.club = club;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
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

    public String getCommunicationEmail() {
        return communicationEmail;
    }

    public void setCommunicationEmail(String communicationEmail) {
        this.communicationEmail = communicationEmail;
    }

    public String getCommunicationWebsite() {
        return communicationWebsite;
    }

    public void setCommunicationWebsite(String communicationWebsite) {
        this.communicationWebsite = communicationWebsite;
    }

    public String getCommunicationInstagram() {
        return communicationInstagram;
    }

    public void setCommunicationInstagram(String communicationInstagram) {
        this.communicationInstagram = communicationInstagram;
    }

    public String getCommunicationFacebook() {
        return communicationFacebook;
    }

    public void setCommunicationFacebook(String communicationFacebook) {
        this.communicationFacebook = communicationFacebook;
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

    // Effective radius for map display: the per-club-year override if set,
    // otherwise the level's default. Null only if this snapshot has neither.
    public Double getEffectiveRadiusKm() {
        if (radiusOverrideKm != null) {
            return radiusOverrideKm;
        }
        return level != null ? level.getRadiusKm() : null;
    }

    public String getFiliere() {
        return filiere;
    }

    public void setFiliere(String filiere) {
        this.filiere = filiere;
    }

    public String getOverlayPositions() {
        return overlayPositions;
    }

    public void setOverlayPositions(String overlayPositions) {
        this.overlayPositions = overlayPositions;
    }
}
