package lu.badminton.map.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// The whole application's editable data in one portable document — levels,
// every club with its per-year snapshots and its weekly schedule. No
// database ids: identity is rebuilt on import. `version` lets a future
// format change stay readable. Unknown fields are ignored on import so a
// newer export still loads into an older build.
@JsonIgnoreProperties(ignoreUnknown = true)
public record DataExport(
        String exportedAt,
        int version,
        List<LevelExport> levels,
        List<ClubExport> clubs) {

    // Bumped to 2 when the single "communication" text field split into 4
    // link fields (email/website/instagram/facebook) — a version-1 file's
    // old "communication" value is simply dropped on import (unknown fields
    // are ignored), not migrated into the new fields.
    public static final int CURRENT_VERSION = 2;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LevelExport(int level, String label, double radiusKm) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClubExport(
            String name,
            String city,
            double latitude,
            double longitude,
            List<ClubYearExport> years,
            List<ScheduleExport> schedule) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClubYearExport(
            int year,
            Integer level,
            Double radiusOverrideKm,
            String filiere,
            String membership,
            String clubActivities,
            String humanResources,
            String finance,
            String communicationEmail,
            String communicationWebsite,
            String communicationInstagram,
            String communicationFacebook,
            String governance,
            String visionStrategy,
            String overlayPositions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScheduleExport(
            String dayOfWeek,
            String startTime,
            String endTime,
            String label) {
    }
}
