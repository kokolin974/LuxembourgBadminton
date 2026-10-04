const LUXEMBOURG_CENTER = [49.8153, 6.1296];

const map = L.map('map').setView(LUXEMBOURG_CENTER, 9);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

// Coverage overlay — hand-rolled canvas layer (heatmap.js was dropped for
// this: its point rendering blends overlapping circles via ordinary alpha
// compositing, not true addition, so a location's calculated overlap count
// never matched what it actually painted, and calibrating against it either
// washed everything out or depended on the current pan/zoom). This layer
// draws flat, uniform-weight disks with the canvas's 'lighter' composite
// mode, which really does add channel values together — so the summed
// density at a pixel is an exact count of how many visible clubs cover it,
// which is exactly what computeMaxOverlapDepth (below) calculates. Rendering
// and calibration can't disagree because they're the same model.
const EARTH_METERS_PER_PIXEL_AT_ZOOM_0 = 156543.03392;

// Tune this to control how soft/smooth the gradient looks — larger is
// smoother (more blur/bleed inside each club's true boundary), smaller is
// crisper. Independent of the clip step below, which stops it from bleeding
// *past* that boundary regardless of how large this is.
const COVERAGE_BLUR_PX = 8;
const COVERAGE_MAX_OPACITY = 0.9;

// Response curve from raw density ratio (0..1) to visual intensity — see the
// comment where this is applied, below. 1.0 = linear (the original, too-flat
// result); lower values push low/mid overlap counts further up the gradient.
const COVERAGE_INTENSITY_CURVE = 0.5;

// Blue -> light blue -> green -> yellow -> red, evenly spaced across the
// 0..1 normalized density range (density / calibrated max).
const COVERAGE_GRADIENT_STOPS = [
    { stop: 0.0, color: [33, 102, 172] },
    { stop: 0.35, color: [103, 169, 207] },
    { stop: 0.55, color: [116, 196, 118] },
    { stop: 0.75, color: [254, 224, 144] },
    { stop: 1.0, color: [215, 48, 39] }
];

function colorForDensity(t) {
    const stops = COVERAGE_GRADIENT_STOPS;
    for (let i = 0; i < stops.length - 1; i++) {
        const a = stops[i];
        const b = stops[i + 1];
        if (t >= a.stop && t <= b.stop) {
            const localT = (t - a.stop) / (b.stop - a.stop);
            return [
                Math.round(a.color[0] + (b.color[0] - a.color[0]) * localT),
                Math.round(a.color[1] + (b.color[1] - a.color[1]) * localT),
                Math.round(a.color[2] + (b.color[2] - a.color[2]) * localT)
            ];
        }
    }
    return stops[stops.length - 1].color;
}

const CoverageLayer = L.Layer.extend({
    initialize: function () {
        this._el = L.DomUtil.create('div', 'leaflet-zoom-hide coverage-layer');
        this._clubs = [];
    },

    onAdd: function (map) {
        this._map = map;
        const size = map.getSize();

        // Offscreen working canvases: raw additive density, then colorized
        // (still unblurred/unclipped). Only the final canvas is visible.
        this._densityCanvas = document.createElement('canvas');
        this._colorCanvas = document.createElement('canvas');
        this._canvas = L.DomUtil.create('canvas', '', this._el);
        this._resizeCanvases(size);

        this._el.style.position = 'absolute';
        this._el.style.top = '0';
        this._el.style.left = '0';
        this._el.style.width = size.x + 'px';
        this._el.style.height = size.y + 'px';
        map.getPanes().overlayPane.appendChild(this._el);
        map.on('moveend', this._reset, this);
        this._reset();
    },

    onRemove: function (map) {
        map.getPanes().overlayPane.removeChild(this._el);
        map.off('moveend', this._reset, this);
    },

    addTo: function (map) {
        map.addLayer(this);
        return this;
    },

    setClubs: function (clubs) {
        this._clubs = clubs;
        this._draw();
    },

    _resizeCanvases: function (size) {
        this._densityCanvas.width = this._colorCanvas.width = this._canvas.width = size.x;
        this._densityCanvas.height = this._colorCanvas.height = this._canvas.height = size.y;
    },

    _reset: function () {
        // Cancels out the overlay pane's own pan/zoom transform so this
        // layer's container stays aligned with the container-pixel
        // coordinates we draw with (same technique Leaflet plugins use).
        const mapPane = this._map.getPanes().mapPane;
        const point = (mapPane && mapPane._leaflet_pos) || { x: 0, y: 0 };
        this._el.style.transform = `translate(${-Math.round(point.x)}px, ${-Math.round(point.y)}px)`;

        const size = this._map.getSize();
        if (this._canvas.width !== size.x || this._canvas.height !== size.y) {
            this._resizeCanvases(size);
            this._el.style.width = size.x + 'px';
            this._el.style.height = size.y + 'px';
        }
        this._draw();
    },

    _draw: function () {
        if (!this._map) {
            return;
        }
        const width = this._canvas.width;
        const height = this._canvas.height;
        const ctx = this._canvas.getContext('2d');
        ctx.clearRect(0, 0, width, height);

        const clubs = this._clubs.filter(club => club.effectiveRadiusKm);
        if (clubs.length === 0) {
            return;
        }

        // Calibrated from the true all-time peak overlap — every year, every
        // club, not just the ones currently drawn (see recomputeCalibratedMax)
        // — so a given spot's color means the same thing regardless of the
        // level filter, an isolated marker view, OR which year is selected.
        // That last part is deliberate: recalibrating per year would stretch
        // even a sparse year to use the full color range, hiding the actual
        // growth in coverage over time that this whole feature exists to show.
        const perClubAlpha = 1 / calibratedMax;

        // Project to the current on-screen circle (center + pixel radius) —
        // the only part of this routine that depends on zoom/pan; calibration
        // above never does.
        const circles = clubs.map(club => {
            const point = this._map.latLngToContainerPoint([club.latitude, club.longitude]);
            const metersPerPixel = EARTH_METERS_PER_PIXEL_AT_ZOOM_0
                * Math.cos(club.latitude * Math.PI / 180) / Math.pow(2, this._map.getZoom());
            const radiusPx = (club.effectiveRadiusKm * 1000) / metersPerPixel;
            return { x: point.x, y: point.y, radius: radiusPx };
        });

        // 1) Density pass: flat disks, true additive blending, so overlap is
        // an exact count (scaled to 0..1 by calibratedMax).
        const densityCtx = this._densityCanvas.getContext('2d');
        densityCtx.clearRect(0, 0, width, height);
        densityCtx.globalCompositeOperation = 'lighter';
        densityCtx.fillStyle = `rgba(0, 0, 0, ${perClubAlpha})`;
        circles.forEach(c => {
            densityCtx.beginPath();
            densityCtx.arc(c.x, c.y, c.radius, 0, 2 * Math.PI);
            densityCtx.fill();
        });
        densityCtx.globalCompositeOperation = 'source-over';

        // 2) Colorize pass: map each pixel's density through the gradient,
        // onto a separate offscreen canvas (still unblurred/unclipped).
        const densityData = densityCtx.getImageData(0, 0, width, height);
        const colorCtx = this._colorCanvas.getContext('2d');
        const colorImage = colorCtx.createImageData(width, height);
        for (let i = 0; i < densityData.data.length; i += 4) {
            const density = Math.min(1, densityData.data[i + 3] / 255);
            if (density <= 0) {
                continue;
            }
            // A straight linear density/max mapping made almost the whole map
            // look faded: with the true country-wide peak far higher than the
            // typical local overlap count, most areas only ever reach a small
            // fraction of max and read as barely-there. Applying a curve here
            // (sqrt = COVERAGE_INTENSITY_CURVE of 0.5) boosts low-to-mid
            // overlap counts well above their raw linear share while the true
            // peak still ends up brightest — "more overlap = more intense"
            // stays true, just compressed less harshly at the low end.
            const intensity = Math.pow(density, COVERAGE_INTENSITY_CURVE);
            const [r, g, b] = colorForDensity(intensity);
            colorImage.data[i] = r;
            colorImage.data[i + 1] = g;
            colorImage.data[i + 2] = b;
            colorImage.data[i + 3] = Math.round(intensity * COVERAGE_MAX_OPACITY * 255);
        }
        colorCtx.putImageData(colorImage, 0, 0);

        // 3) Final pass: clip to the true (unblurred) circle shapes, then
        // draw the colorized layer through a blur — clipping happens on the
        // destination, so nothing can paint past a club's actual radius no
        // matter how far the blur would otherwise spread it.
        ctx.save();
        ctx.beginPath();
        circles.forEach(c => {
            ctx.moveTo(c.x + c.radius, c.y);
            ctx.arc(c.x, c.y, c.radius, 0, 2 * Math.PI);
        });
        ctx.clip();

        // Hard cut at the Luxembourg border: a second clip() intersects with
        // the circle clip above, so heat survives only where a club's circle
        // and the country overlap. Skipped (heat just shows unclipped) until
        // the border file has loaded, or if it fails to.
        if (luxembourgBorderRings) {
            ctx.beginPath();
            luxembourgBorderRings.forEach(ring => {
                ring.forEach(([lat, lng], i) => {
                    const p = this._map.latLngToContainerPoint([lat, lng]);
                    if (i === 0) {
                        ctx.moveTo(p.x, p.y);
                    } else {
                        ctx.lineTo(p.x, p.y);
                    }
                });
                ctx.closePath();
            });
            ctx.clip();
        }

        ctx.filter = `blur(${COVERAGE_BLUR_PX}px)`;
        ctx.drawImage(this._colorCanvas, 0, 0);
        ctx.filter = 'none';
        ctx.restore();
    }
});

// Projects lat/lng to flat local meters around a reference point. Luxembourg
// is small enough that this equirectangular approximation is accurate enough
// for comparing distances/radii — it's only used to find the overlap peak,
// not for anything geographic that gets drawn.
function projectToMeters(lat, lng, refLat, refLng) {
    const metersPerDegLat = 111320;
    const metersPerDegLng = 111320 * Math.cos(refLat * Math.PI / 180);
    return {
        x: (lng - refLng) * metersPerDegLng,
        y: (lat - refLat) * metersPerDegLat
    };
}

// Returns the 0, 1, or 2 points (in the same local-meters space as the
// circles) where two circles' boundaries cross, or [] if they don't
// intersect (too far apart, or one fully contains the other).
function circleIntersections(c1, c2) {
    const dx = c2.x - c1.x;
    const dy = c2.y - c1.y;
    const d = Math.sqrt(dx * dx + dy * dy);
    if (d === 0 || d > c1.radius + c2.radius || d < Math.abs(c1.radius - c2.radius)) {
        return [];
    }
    const a = (c1.radius * c1.radius - c2.radius * c2.radius + d * d) / (2 * d);
    const h = Math.sqrt(Math.max(0, c1.radius * c1.radius - a * a));
    const xm = c1.x + (a * dx) / d;
    const ym = c1.y + (a * dy) / d;
    const rx = -dy * (h / d);
    const ry = dx * (h / d);
    return [
        { x: xm + rx, y: ym + ry },
        { x: xm - rx, y: ym - ry }
    ];
}

// The true peak overlap depth of a set of circles always occurs either at
// one circle's own center, or at a point where two circle boundaries cross —
// coverage depth is constant in between such points, so testing just those
// candidates finds the exact peak without scanning the whole map.
function computeMaxOverlapDepth(clubs) {
    const withRadius = clubs.filter(club => club.effectiveRadiusKm);
    if (withRadius.length === 0) {
        return 0;
    }

    const refLat = withRadius.reduce((sum, c) => sum + c.latitude, 0) / withRadius.length;
    const refLng = withRadius.reduce((sum, c) => sum + c.longitude, 0) / withRadius.length;
    const circles = withRadius.map(club => {
        const p = projectToMeters(club.latitude, club.longitude, refLat, refLng);
        return { x: p.x, y: p.y, radius: club.effectiveRadiusKm * 1000 };
    });

    const candidates = circles.map(c => ({ x: c.x, y: c.y }));
    for (let i = 0; i < circles.length; i++) {
        for (let j = i + 1; j < circles.length; j++) {
            candidates.push(...circleIntersections(circles[i], circles[j]));
        }
    }

    const EPSILON_METERS = 0.5; // tolerance for candidates that sit exactly on a circle's boundary
    let maxDepth = 0;
    candidates.forEach(point => {
        const depth = circles.filter(c => {
            const dx = point.x - c.x;
            const dy = point.y - c.y;
            return Math.sqrt(dx * dx + dy * dy) <= c.radius + EPSILON_METERS;
        }).length;
        maxDepth = Math.max(maxDepth, depth);
    });
    return maxDepth;
}

const coverageLayer = new CoverageLayer();
let heatmapVisible = false;

// Luxembourg's outer border (OpenStreetMap relation 2171347, ~2000 points),
// loaded once and kept as [lat, lng] rings for CoverageLayer to clip to.
// GeoJSON stores [lng, lat], hence the swap. Only each polygon's outer ring is
// used — the country has no holes. Null until loaded.
let luxembourgBorderRings = null;

fetch('/geo/luxembourg.geojson')
    .then(response => response.json())
    .then(geo => {
        const geometry = geo.features ? geo.features[0].geometry : geo.geometry || geo;
        const polygons = geometry.type === 'MultiPolygon' ? geometry.coordinates : [geometry.coordinates];
        luxembourgBorderRings = polygons.map(polygon => polygon[0].map(([lng, lat]) => [lat, lng]));
        refreshHeatmap(); // redraw in case coverage was switched on before this arrived
    })
    .catch(err => console.error('Failed to load the Luxembourg border; heatmap stays unclipped', err));

// Headroom above the true peak, per product decision: without it, the
// single most-overlapped spot would render as pure max-color,
// indistinguishable from "almost as covered" areas.
const OVERLAP_HEADROOM = 1;

// All club-year snapshots that have ever existed, keyed by year — populated
// once from /api/clubs/history at load, and kept in sync (this year's entry
// only) whenever the year changes or a radius override is saved. Used only
// to calibrate the heatmap; never to decide what's shown on the map.
const clubHistoryByYear = new Map();

// The fixed color-scale ceiling described above CoverageLayer._draw — the
// highest overlap depth reached by any single year, ever. Recomputed
// whenever clubHistoryByYear changes.
let calibratedMax = 1;

function recomputeCalibratedMax() {
    let peak = 0;
    clubHistoryByYear.forEach(clubsForYear => {
        peak = Math.max(peak, computeMaxOverlapDepth(clubsForYear));
    });
    calibratedMax = Math.max(1, peak + OVERLAP_HEADROOM);
}

function setHeatmapClubs(clubs) {
    if (!heatmapVisible) {
        return;
    }
    coverageLayer.setClubs(clubs);
}

// Refreshes to whatever the level filter currently allows. When a single
// club is isolated (marker clicked), setHeatmapClubs([club]) is used instead
// so the heatmap matches what's actually visible on the map.
function refreshHeatmap() {
    setHeatmapClubs(getVisibleClubs());
}

// Marker icon reflects the club's level (clubLevel1.png..clubLevel7.png); falls
// back to the plain house icon if the club has no level or its image fails to load.
const DEFAULT_ICON_URL = '/img/house-marker.png';

// Clubs not affiliated with the federation have no level, so they get their
// own icon keyed on the filière value instead (same fallback to the plain
// house if the image is missing). Kept in sync by hand with
// ClubYear.FILIERE_NON_AFFILIE on the server.
const NON_AFFILIE_FILIERE = 'Non affilié';
const NON_AFFILIE_ICON_URL = '/img/notAffiliated.png';

// Club-finder state: the pin dropped at a geocoded address, and the ids of
// the clubs currently shown as suggestions for it (their markers get a ring
// — see iconForClub). Both cleared when the finder panel is closed.
let addressMarker = null;
let suggestedClubIds = new Set();

// Icon shrinks as you zoom in — at country-wide zoom a bigger icon stays
// visible, but once you're zoomed into a small area a 40px house photo per
// marker gets overwhelming, especially where several markers sit close
// together. Tuned in bands rather than a continuous formula for predictability.
// This is the base size for a mid-level club — iconForClub scales it further
// by the club's own level (see LEVEL_SIZE_FACTOR_MIN/MAX below).
function iconSizeForZoom(zoom) {
    if (zoom <= 10) return 40;
    if (zoom <= 12) return 32;
    if (zoom <= 14) return 26;
    if (zoom <= 16) return 22;
    return 18;
}

// Higher levels render bigger: 1 - Découverte is the smallest, 7 - Platine
// the largest, linearly in between. Applied on top of the zoom-based base
// size above, so the level ordering stays visible at every zoom.
const LEVEL_SIZE_FACTOR_MIN = 0.7;
const LEVEL_SIZE_FACTOR_MAX = 1.3;

function levelSizeFactor(level) {
    if (!level) {
        return 1;
    }
    const t = (level.level - 1) / 6; // 0 (Découverte) .. 1 (Platine)
    return LEVEL_SIZE_FACTOR_MIN + (LEVEL_SIZE_FACTOR_MAX - LEVEL_SIZE_FACTOR_MIN) * t;
}

// The name label is a fixed size for every marker — it does NOT scale with
// the house icon (which varies by zoom and level). Full name, wrapped: the
// box is wide enough that most names take two lines; longer ones wrap
// further and overflow the declared icon height downward (visible, though
// only the first line stays clickable).
const MARKER_LABEL_FONT_PX = 10;
const MARKER_LABEL_WIDTH_PX = 130;
const MARKER_LABEL_LINE_HEIGHT_PX = Math.round(MARKER_LABEL_FONT_PX * 1.35) + 3;

function iconForClub(club, baseSize) {
    let url = DEFAULT_ICON_URL;
    if (club.filiere === NON_AFFILIE_FILIERE) {
        url = NON_AFFILIE_ICON_URL;
    } else if (club.level) {
        url = `/img/clubLevel${club.level.level}.png`;
    }
    const size = Math.round(baseSize * levelSizeFactor(club.level));

    const labelFontSize = MARKER_LABEL_FONT_PX;
    const labelWidth = MARKER_LABEL_WIDTH_PX;
    const labelHeight = MARKER_LABEL_LINE_HEIGHT_PX;
    const name = escapeHtml(club.name);
    const suggestedClass = suggestedClubIds.has(club.id) ? ' club-marker-wrap--suggested' : '';

    return L.divIcon({
        html: `
            <div class="club-marker-wrap${suggestedClass}" style="width:${labelWidth}px;">
                <img class="club-marker-img" style="width:${size}px; height:${size}px;"
                     src="${url}" onerror="this.src='${DEFAULT_ICON_URL}'"/>
                <div class="club-marker-label" style="font-size:${labelFontSize}px;" title="${name}">${name}</div>
            </div>
        `,
        className: 'club-marker-icon',
        iconSize: [labelWidth, size + labelHeight],
        iconAnchor: [labelWidth / 2, size],
        popupAnchor: [0, -size * 0.9]
    });
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value ?? '';
    return div.innerHTML;
}

// Levels of the club house diagram (discovery_house_clean.svg), roof to foundation.
// Each maps to one Club field and to a fixed vertical band on the image — the
// "ov-*" class controls where its text box sits, positioned below that band's
// baked-in icon/title so it reads as a label under each level's heading.
const LEVELS = [
    { field: 'visionStrategy', cssClass: 'ov-vision-strategy' },
    { field: 'governance', cssClass: 'ov-governance' },
    // Communication has no single text field — it's 4 separate optional
    // links, rendered as clickable <a> tags instead of bullet-list text
    // (see communicationLinksHtml). "field" here only names the overlay's
    // drag-position slot, matching the others.
    { field: 'communication', cssClass: 'ov-communication', isCommunication: true },
    { field: 'finance', cssClass: 'ov-finance' },
    { field: 'humanResources', cssClass: 'ov-human-resources' },
    { field: 'clubActivities', cssClass: 'ov-club-activities' },
    { field: 'membership', cssClass: 'ov-membership' }
];

// Builds the Communication level's content: one clickable link per channel
// the club actually has, skipping the rest. Email becomes a mailto: link.
function communicationLinksHtml(club) {
    const links = [];
    if (club.communicationEmail) {
        links.push({ label: 'Email', href: `mailto:${club.communicationEmail}` });
    }
    if (club.communicationWebsite) {
        links.push({ label: 'Website', href: club.communicationWebsite });
    }
    if (club.communicationInstagram) {
        links.push({ label: 'Instagram', href: club.communicationInstagram });
    }
    if (club.communicationFacebook) {
        links.push({ label: 'Facebook', href: club.communicationFacebook });
    }
    return links;
}

// Saved drag positions come back from the API as a JSON string (see
// Club.overlayPositions) — {"governance": {"left": 52.3, "top": 30.1}, ...}.
function parseOverlayPositions(club) {
    if (!club.overlayPositions) {
        return {};
    }
    try {
        return JSON.parse(club.overlayPositions);
    } catch (err) {
        console.error('Failed to parse overlayPositions', err);
        return {};
    }
}

// Multi-fact fields (e.g. manager/coach/officials, three fee amounts) are
// stored one fact per line — rendered as a bullet list when there's more
// than one line, otherwise as plain text. data-field identifies each block
// for the drag handler (see makeOverlayDraggable); a saved position (if this
// club has one for that level) overrides the CSS default via inline style.
function levelOverlaysHtml(club) {
    const positions = parseOverlayPositions(club);
    return LEVELS.map(level => {
        const saved = positions[level.field];
        const styleAttr = saved
            ? ` style="left:${saved.left}%; top:${saved.top}%; right:auto;"`
            : '';

        if (level.isCommunication) {
            const links = communicationLinksHtml(club);
            if (links.length === 0) {
                return `<div class="club-overlay ${level.cssClass}" data-field="${level.field}"${styleAttr}></div>`;
            }
            const content = `<ul>${links.map(l =>
                `<li><a href="${escapeHtml(l.href)}" target="_blank" rel="noopener noreferrer" draggable="false">${l.label}</a></li>`
            ).join('')}</ul>`;
            const tooltip = escapeHtml(links.map(l => l.label).join(', '));
            return `<div class="club-overlay ${level.cssClass}" data-field="${level.field}"${styleAttr} title="${tooltip}">${content}</div>`;
        }

        const text = club[level.field];
        if (!text) {
            return `<div class="club-overlay ${level.cssClass}" data-field="${level.field}"${styleAttr}></div>`;
        }
        const lines = text.split('\n').map(line => line.trim()).filter(Boolean);
        const content = lines.length > 1
            ? `<ul>${lines.map(line => `<li>${escapeHtml(line)}</li>`).join('')}</ul>`
            : escapeHtml(lines[0] ?? text);
        const tooltip = escapeHtml(lines.join(' — '));
        return `<div class="club-overlay ${level.cssClass}" data-field="${level.field}"${styleAttr} title="${tooltip}">${content}</div>`;
    }).join('');
}

const panel = document.getElementById('club-panel');
const panelContent = document.getElementById('club-panel-content');
const panelClose = document.getElementById('club-panel-close');

const schedulePanel = document.getElementById('schedule-panel');
const schedulePanelContent = document.getElementById('schedule-panel-content');
const schedulePanelClose = document.getElementById('schedule-panel-close');

// FullCalendar's daysOfWeek uses 0=Sunday..6=Saturday.
const DAY_NAME_TO_INDEX = { Sunday: 0, Monday: 1, Tuesday: 2, Wednesday: 3, Thursday: 4, Friday: 5, Saturday: 6 };
const DAY_INDEX_TO_NAME = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function saveSlot(clubId, slotId, dayOfWeek, startTime, endTime, label) {
    const url = slotId
        ? `/api/clubs/${clubId}/schedule/${slotId}`
        : `/api/clubs/${clubId}/schedule`;
    return fetch(url, {
        method: slotId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dayOfWeek, startTime, endTime, label: label || null })
    }).then(response => response.json());
}

function deleteSlotRequest(clubId, slotId) {
    return fetch(`/api/clubs/${clubId}/schedule/${slotId}`, { method: 'DELETE' });
}

// Recurring (daysOfWeek-based) events, one per weekday the slot repeats on —
// no real date, since this is a repeating weekly pattern, not a specific week.
// title (if set) shows on the event block alongside FullCalendar's own
// default time-range text.
function slotToEvent(slot) {
    return {
        id: String(slot.id),
        daysOfWeek: [DAY_NAME_TO_INDEX[slot.dayOfWeek]],
        startTime: slot.startTime,
        endTime: slot.endTime,
        title: slot.label || '',
        extendedProps: { label: slot.label || '' }
    };
}

function pad2(n) {
    return String(n).padStart(2, '0');
}

function timeStringFromDate(date) {
    return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

let scheduleCalendar = null;
let scheduleEditPopover = null;

function closeScheduleEditPopover() {
    if (scheduleEditPopover) {
        scheduleEditPopover.remove();
        scheduleEditPopover = null;
    }
    document.removeEventListener('pointerdown', handleOutsideClick, true);
}

function handleOutsideClick(event) {
    if (scheduleEditPopover && !scheduleEditPopover.contains(event.target)) {
        closeScheduleEditPopover();
    }
}

// Popover for editing/deleting the slot behind a clicked event. Recurring
// events don't have simply-mutable start/end props, so a saved edit is
// applied by removing and re-adding the calendar event rather than mutating
// it in place — the DB row itself is a normal PUT, same id throughout.
function openScheduleEditPopover(clubId, calendarEvent, clientX, clientY) {
    closeScheduleEditPopover();

    const dayName = DAY_INDEX_TO_NAME[calendarEvent.start.getDay()];
    const popover = document.createElement('div');
    popover.className = 'schedule-edit-popover';
    popover.style.left = `${Math.min(clientX, window.innerWidth - 220)}px`;
    popover.style.top = `${Math.min(clientY, window.innerHeight - 140)}px`;
    const currentLabel = calendarEvent.extendedProps.label || '';
    popover.innerHTML = `
        <label>Start <input type="time" class="schedule-edit-start" value="${timeStringFromDate(calendarEvent.start)}"/></label>
        <label>End <input type="time" class="schedule-edit-end" value="${timeStringFromDate(calendarEvent.end)}"/></label>
        <label>Text <input type="text" class="schedule-edit-label" value="${escapeHtml(currentLabel)}" placeholder="e.g. Youth"/></label>
        <div class="schedule-edit-popover-actions">
            <button type="button" class="schedule-edit-delete">Delete</button>
            <button type="button" class="schedule-edit-close">Done</button>
        </div>
    `;
    document.body.appendChild(popover);
    scheduleEditPopover = popover;
    setTimeout(() => document.addEventListener('pointerdown', handleOutsideClick, true), 0);

    const startInput = popover.querySelector('.schedule-edit-start');
    const endInput = popover.querySelector('.schedule-edit-end');
    const labelInput = popover.querySelector('.schedule-edit-label');

    function persist() {
        saveSlot(clubId, calendarEvent.id, dayName, startInput.value, endInput.value, labelInput.value).then(saved => {
            calendarEvent.remove();
            scheduleCalendar.addEvent(slotToEvent(saved));
        });
    }

    startInput.addEventListener('change', persist);
    endInput.addEventListener('change', persist);
    labelInput.addEventListener('change', persist);

    popover.querySelector('.schedule-edit-delete').addEventListener('click', () => {
        deleteSlotRequest(clubId, calendarEvent.id);
        calendarEvent.remove();
        closeScheduleEditPopover();
    });

    popover.querySelector('.schedule-edit-close').addEventListener('click', closeScheduleEditPopover);
}

function openSchedulePanel(club) {
    schedulePanelContent.innerHTML = `
        <h2>${escapeHtml(club.name)} — Weekly schedule</h2>
        <p class="schedule-hint">Drag across empty time to add a session; click a session to edit or delete it.</p>
        <div id="schedule-calendar"></div>
    `;
    schedulePanel.hidden = false;
    closeScheduleEditPopover();

    if (scheduleCalendar) {
        scheduleCalendar.destroy();
    }

    fetch(`/api/clubs/${club.id}/schedule`)
        .then(response => response.json())
        .then(slots => {
            const calendarEl = document.getElementById('schedule-calendar');
            scheduleCalendar = new FullCalendar.Calendar(calendarEl, {
                initialView: 'timeGridWeek',
                headerToolbar: false,
                dayHeaderFormat: { weekday: 'short' },
                // 24-hour, matching how every time is entered/stored elsewhere
                // in this app — avoids an ambiguous, sometimes-truncated am/pm.
                slotLabelFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
                eventTimeFormat: { hour: '2-digit', minute: '2-digit', hour12: false },
                allDaySlot: false,
                slotMinTime: '08:00:00',
                slotMaxTime: '23:00:00',
                slotDuration: '00:30:00',
                height: 560,
                nowIndicator: false,
                selectable: true,
                selectMirror: true,
                eventColor: '#1f6f4d',
                events: slots.map(slotToEvent),
                select: info => {
                    const dayName = DAY_INDEX_TO_NAME[info.start.getDay()];
                    saveSlot(club.id, null, dayName, timeStringFromDate(info.start), timeStringFromDate(info.end))
                        .then(saved => {
                            scheduleCalendar.unselect();
                            scheduleCalendar.addEvent(slotToEvent(saved));
                        });
                },
                eventClick: info => {
                    openScheduleEditPopover(club.id, info.event, info.jsEvent.clientX, info.jsEvent.clientY);
                }
            });
            scheduleCalendar.render();
        })
        .catch(err => console.error('Failed to load schedule', err));
}

schedulePanelClose.addEventListener('click', () => {
    schedulePanel.hidden = true;
    closeScheduleEditPopover();
});

let allMarkers = [];

// Levels currently checked in the filter box. A marker is only ever on the
// map if its club's level is in this set — clicking a marker to isolate it
// temporarily overrides that (hiding the rest), but closing the panel goes
// back through applyFilter() rather than blindly re-showing everyone, so
// filtered-out clubs stay hidden.
let selectedLevels = new Set();

// Filières currently checked. Every club falls in exactly one bucket: its
// filière string, or NO_FILIERE when it has none.
const NO_FILIERE = 'No filière';
let selectedFilieres = new Set();

function markerPassesFilter(marker) {
    const level = marker.club.level;
    const levelOk = !level || selectedLevels.has(level.level);
    const filiereOk = selectedFilieres.has(marker.club.filiere || NO_FILIERE);
    return levelOk && filiereOk;
}

function applyFilter() {
    allMarkers.forEach(marker => {
        // While the coverage heatmap is showing, the club icons (and their
        // name labels) are hidden so the heat is easy to read. This only
        // affects what's on the map — which clubs the heatmap counts still
        // comes from markerPassesFilter alone (see getVisibleClubs).
        const shouldShow = !heatmapVisible && markerPassesFilter(marker);
        const isShown = map.hasLayer(marker);
        if (shouldShow && !isShown) {
            marker.addTo(map);
        } else if (!shouldShow && isShown) {
            map.removeLayer(marker);
        }
    });
    refreshHeatmap();
}

// The clubs a consumer (e.g. a coverage heatmap) should currently account
// for — respects the level filter, but not a temporary marker-isolation view.
function getVisibleClubs() {
    return allMarkers.filter(markerPassesFilter).map(marker => marker.club);
}

function hideOtherMarkers(selectedMarker) {
    allMarkers.forEach(marker => {
        if (marker !== selectedMarker && map.hasLayer(marker)) {
            map.removeLayer(marker);
        }
    });
}

// Radius bounds for the slider — deliberately wider than the 5-30km level
// range, since it's an override and might reasonably need to go past it.
const RADIUS_MIN_KM = 1;
const RADIUS_MAX_KM = 50;
const RADIUS_STEP_KM = 0.5;

function radiusLabelText(km) {
    return km != null ? `${km} km` : 'not set';
}

function radiusControlHtml(club) {
    const levelDefault = club.level ? club.level.radiusKm : null;
    // effectiveRadiusKm is null when the club has no level and no override —
    // it genuinely has no area of influence yet, so say so rather than
    // showing the slider's floor as if it were a real value.
    const effective = club.effectiveRadiusKm ?? null;
    const sliderValue = effective ?? levelDefault ?? RADIUS_MIN_KM;
    const hasOverride = club.radiusOverrideKm != null;
    return `
        <div class="club-radius">
            <label for="club-radius-slider">Radius of influence: <span id="club-radius-value">${radiusLabelText(effective)}</span></label>
            <input type="range" id="club-radius-slider" min="${RADIUS_MIN_KM}" max="${RADIUS_MAX_KM}"
                   step="${RADIUS_STEP_KM}" value="${sliderValue}"/>
            <button type="button" id="club-radius-reset" class="club-radius-reset" ${hasOverride ? '' : 'hidden'}>
                Reset to level default (${levelDefault != null ? levelDefault + ' km' : '—'})
            </button>
        </div>
    `;
}

// Keeps the calibration snapshot (see recomputeCalibratedMax) in sync after
// a radius override changes, so the heatmap's fixed color scale still
// reflects this club's current radius rather than a stale cached one.
function updateHistoryEntry(club) {
    const yearList = clubHistoryByYear.get(club.year);
    if (!yearList) {
        return;
    }
    const index = yearList.findIndex(c => c.id === club.id);
    if (index !== -1) {
        yearList[index] = { ...yearList[index], radiusOverrideKm: club.radiusOverrideKm, effectiveRadiusKm: club.effectiveRadiusKm };
    }
    recomputeCalibratedMax();
}

function saveRadius(club, radiusOverrideKm) {
    return fetch(`/api/clubs/${club.id}/years/${club.year}/radius`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ radiusOverrideKm })
    })
        .then(response => response.json())
        .then(updated => {
            club.radiusOverrideKm = updated.radiusOverrideKm;
            club.effectiveRadiusKm = updated.effectiveRadiusKm;
            updateHistoryEntry(club);
        })
        .catch(err => console.error('Failed to save radius', err));
}

function wireRadiusControl(club) {
    const slider = document.getElementById('club-radius-slider');
    const valueLabel = document.getElementById('club-radius-value');
    const resetButton = document.getElementById('club-radius-reset');

    slider.addEventListener('input', () => {
        valueLabel.textContent = radiusLabelText(parseFloat(slider.value));
    });

    slider.addEventListener('change', () => {
        const value = parseFloat(slider.value);
        saveRadius(club, value).then(() => {
            resetButton.hidden = false;
            setHeatmapClubs([club]);
        });
    });

    resetButton.addEventListener('click', () => {
        saveRadius(club, null).then(() => {
            const levelDefault = club.level ? club.level.radiusKm : null;
            slider.value = levelDefault ?? RADIUS_MIN_KM;
            valueLabel.textContent = radiusLabelText(levelDefault);
            resetButton.hidden = true;
            setHeatmapClubs([club]);
        });
    });
}

// Lets a level's text block be click-and-dragged to wherever actually fits
// for that club's content, rather than relying on one guessed default
// position for every club. Saved on drop via PATCH .../overlay-positions, so
// it's still there next time this club's panel is opened.
function makeOverlayDraggable(el, wrapEl, club) {
    // Drag vs. click is told apart by actual pointer movement, not by what's
    // under the cursor — the Communication block is entirely links (Email/
    // Website/Instagram/Facebook), so "is this a link?" would always say
    // yes and the block could never be dragged. A short press-and-release
    // with no movement reaches the browser as an ordinary click (so a link
    // still navigates); movement past DRAG_THRESHOLD_PX becomes a drag, and
    // the click that the browser fires right after releasing a real drag is
    // swallowed so it doesn't also trigger a link underneath.
    const DRAG_THRESHOLD_PX = 4;
    let pointerId = null;
    let dragging = false;
    let suppressNextClick = false;
    let startX, startY, startLeftPct, startTopPct;
    let finalLeftPct, finalTopPct;

    el.addEventListener('pointerdown', event => {
        pointerId = event.pointerId;
        dragging = false;
        startX = event.clientX;
        startY = event.clientY;
        const wrapRect = wrapEl.getBoundingClientRect();
        const elRect = el.getBoundingClientRect();
        startLeftPct = ((elRect.left - wrapRect.left) / wrapRect.width) * 100;
        startTopPct = ((elRect.top - wrapRect.top) / wrapRect.height) * 100;
    });

    el.addEventListener('pointermove', event => {
        if (pointerId === null || event.pointerId !== pointerId) {
            return;
        }
        const dx = event.clientX - startX;
        const dy = event.clientY - startY;
        if (!dragging) {
            if (Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) {
                return; // still might just be a click/tap — don't commit yet
            }
            dragging = true;
            // Only grab capture once this is confirmed to be a real drag —
            // doing it on pointerdown unconditionally risked retargeting the
            // click a plain tap on a link relies on to navigate.
            try {
                el.setPointerCapture(pointerId);
            } catch (err) {
                // Capture is a nice-to-have (keeps the drag going if the
                // pointer strays outside the element); losing it shouldn't
                // abort the rest of this handler.
            }
        }
        event.preventDefault();
        const wrapRect = wrapEl.getBoundingClientRect();
        finalLeftPct = startLeftPct + (dx / wrapRect.width) * 100;
        finalTopPct = startTopPct + (dy / wrapRect.height) * 100;
        el.style.left = `${finalLeftPct}%`;
        el.style.top = `${finalTopPct}%`;
        el.style.right = 'auto';
    });

    el.addEventListener('pointerup', event => {
        if (pointerId === null || event.pointerId !== pointerId) {
            return;
        }
        pointerId = null;
        // A plain click (no pointermove past the threshold) never set
        // final*Pct — nothing actually moved, so there's nothing to save,
        // and any link under the pointer navigates as normal.
        if (!dragging || finalLeftPct === undefined) {
            dragging = false;
            return;
        }
        dragging = false;
        suppressNextClick = true;

        const positions = parseOverlayPositions(club);
        positions[el.dataset.field] = { left: finalLeftPct, top: finalTopPct };
        club.overlayPositions = JSON.stringify(positions);

        fetch(`/api/clubs/${club.id}/years/${club.year}/overlay-positions`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(positions)
        }).catch(err => console.error('Failed to save overlay position', err));
    });

    // Belt-and-braces alongside the `buttons === 0` check above — if the
    // browser cancels the gesture outright (another way pointerup can fail
    // to arrive), drop any in-progress drag without saving a position.
    el.addEventListener('pointercancel', event => {
        if (pointerId === null || event.pointerId !== pointerId) {
            return;
        }
        pointerId = null;
        dragging = false;
    });

    // Capture phase, so this runs before a link's own navigation — swallows
    // the click the browser fires right after releasing a real drag.
    el.addEventListener('click', event => {
        if (suppressNextClick) {
            suppressNextClick = false;
            event.preventDefault();
            event.stopPropagation();
        }
    }, true);

    // Belt-and-suspenders for the CSS user-drag:none on links (support for
    // that property varies) — a link's native browser drag-and-drop would
    // otherwise hijack a press-and-move gesture starting on its text,
    // pre-empting the custom drag handling above.
    el.addEventListener('dragstart', event => event.preventDefault());
}

function openClubPanel(club, marker) {
    panelContent.innerHTML = `
        <div class="club-house">
            <div class="club-house-wrap">
                <img class="club-house-img" src="/img/discovery_house_clean.svg" alt="Club levels diagram"/>
                ${levelOverlaysHtml(club)}
                <div class="club-house-header">
                    <h2>${escapeHtml(club.name)} — ${escapeHtml(club.city)}</h2>
                    ${radiusControlHtml(club)}
                </div>
            </div>
        </div>
    `;
    panel.hidden = false;

    const wrapEl = panelContent.querySelector('.club-house-wrap');
    panelContent.querySelectorAll('.club-overlay').forEach(el => makeOverlayDraggable(el, wrapEl, club));

    wireRadiusControl(club);
    hideOtherMarkers(marker);
    setHeatmapClubs([club]);
    openSchedulePanel(club);
}

panelClose.addEventListener('click', () => {
    panel.hidden = true;
    schedulePanel.hidden = true;
    closeScheduleEditPopover();
    applyFilter();
});

// Clubs whose coordinates round to the same spot (e.g. several clubs in one
// city) would otherwise stack into a single unclickable marker. Spread each
// group evenly around a small circle so every marker stays visible and
// clickable, without changing the underlying stored coordinates.
function spreadOverlappingClubs(clubs) {
    const groups = new Map();
    clubs.forEach(club => {
        const key = `${club.latitude.toFixed(3)},${club.longitude.toFixed(3)}`;
        if (!groups.has(key)) {
            groups.set(key, []);
        }
        groups.get(key).push(club);
    });

    const positioned = [];
    groups.forEach(group => {
        if (group.length === 1) {
            const club = group[0];
            positioned.push({ club, lat: club.latitude, lng: club.longitude });
            return;
        }
        // ~450m at this latitude — enough that even the largest (zoomed-out,
        // 40px) icons don't overlap for two clubs in the same city.
        const radiusDegrees = 0.004;
        group.forEach((club, index) => {
            const angle = (2 * Math.PI * index) / group.length;
            positioned.push({
                club,
                lat: club.latitude + radiusDegrees * Math.cos(angle),
                lng: club.longitude + radiusDegrees * Math.sin(angle)
            });
        });
    });
    return positioned;
}

const levelFilter = document.getElementById('level-filter');

function renderLevelFilter(levels, filieres) {
    const levelCheckboxes = levels.map(level => `
        <label>
            <input type="checkbox" class="level-filter-checkbox" value="${level.level}" checked/>
            ${level.level} - ${escapeHtml(level.label)}
        </label>
    `).join('');

    const filiereCheckboxes = filieres.map((filiere, index) => `
        <label>
            <input type="checkbox" class="filiere-filter-checkbox" data-filiere-index="${index}" checked/>
            ${escapeHtml(filiere)}
        </label>
    `).join('');

    levelFilter.innerHTML = `
        <h3>Filter by level</h3>
        ${levelCheckboxes}
        <div class="level-filter-actions">
            <button type="button" id="level-filter-all">All</button>
            <button type="button" id="level-filter-none">None</button>
        </div>
        <h3 class="level-filter-subhead">Filter by filière</h3>
        ${filiereCheckboxes}
        <label class="heatmap-toggle-label">
            <input type="checkbox" id="heatmap-toggle"/>
            Show coverage
        </label>
    `;

    document.getElementById('heatmap-toggle').addEventListener('change', event => {
        heatmapVisible = event.target.checked;
        if (heatmapVisible) {
            coverageLayer.addTo(map);
        } else {
            map.removeLayer(coverageLayer);
        }
        // Hides the club icons when coverage turns on and brings them back
        // when it turns off; also redraws the heatmap (applyFilter ends with
        // refreshHeatmap).
        applyFilter();
    });

    levelFilter.querySelectorAll('.level-filter-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            const value = parseInt(checkbox.value, 10);
            if (checkbox.checked) {
                selectedLevels.add(value);
            } else {
                selectedLevels.delete(value);
            }
            applyFilter();
        });
    });

    // data-filiere-index rather than value=, so a filière string with an
    // awkward character can't break the checkbox markup.
    levelFilter.querySelectorAll('.filiere-filter-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            const filiere = filieres[parseInt(checkbox.dataset.filiereIndex, 10)];
            if (checkbox.checked) {
                selectedFilieres.add(filiere);
            } else {
                selectedFilieres.delete(filiere);
            }
            applyFilter();
        });
    });

    document.getElementById('level-filter-all').addEventListener('click', () => {
        selectedLevels = new Set(levels.map(level => level.level));
        levelFilter.querySelectorAll('.level-filter-checkbox').forEach(cb => { cb.checked = true; });
        applyFilter();
    });

    document.getElementById('level-filter-none').addEventListener('click', () => {
        selectedLevels = new Set();
        levelFilter.querySelectorAll('.level-filter-checkbox').forEach(cb => { cb.checked = false; });
        applyFilter();
    });
}

const yearFilter = document.getElementById('year-filter');
let currentYear = null;

function renderYearFilter(years) {
    const buttons = years.map(year => `
        <button type="button" class="year-filter-btn ${year === currentYear ? 'active' : ''}" data-year="${year}">${year}</button>
    `).join('');
    yearFilter.innerHTML = `
        <h3>Year</h3>
        <div class="year-filter-buttons">${buttons}</div>
    `;

    yearFilter.querySelectorAll('.year-filter-btn').forEach(button => {
        button.addEventListener('click', () => {
            const year = parseInt(button.dataset.year, 10);
            if (year === currentYear) {
                return;
            }
            currentYear = year;
            yearFilter.querySelectorAll('.year-filter-btn').forEach(btn => {
                btn.classList.toggle('active', parseInt(btn.dataset.year, 10) === year);
            });
            panel.hidden = true;
            schedulePanel.hidden = true;
            closeScheduleEditPopover();
            // Suggestions are year-specific — drop the old ones rather than
            // showing a stale ranking against the new year's clubs.
            suggestionPanel.hidden = true;
            clearFinderResults();
            loadClubsForYear(year);
        });
    });
}

// Swaps the marker set to the given year's clubs, keeping the level filter
// and heatmap calibration in sync. Also refreshes this year's slice of
// clubHistoryByYear, in case a club was added/edited since the initial load.
function loadClubsForYear(year) {
    return fetch(`/api/clubs?year=${year}`)
        .then(response => response.json())
        .then(clubs => {
            allMarkers.forEach(marker => {
                if (map.hasLayer(marker)) {
                    map.removeLayer(marker);
                }
            });

            const size = iconSizeForZoom(map.getZoom());
            allMarkers = spreadOverlappingClubs(clubs).map(({ club, lat, lng }) => {
                const marker = L.marker([lat, lng], { icon: iconForClub(club, size) });
                marker.club = club;
                marker.on('click', () => openClubPanel(club, marker));
                return marker;
            });

            clubHistoryByYear.set(year, clubs);
            recomputeCalibratedMax();
            applyFilter();
        })
        .catch(err => console.error('Failed to load clubs for year ' + year, err));
}

// --- Club finder (suggest clubs near an address) ----------------------------

const clubFinder = document.getElementById('club-finder');
const suggestionPanel = document.getElementById('suggestion-panel');
const suggestionPanelContent = document.getElementById('suggestion-panel-content');
const suggestionPanelClose = document.getElementById('suggestion-panel-close');

// Autocomplete state (Photon suggestions under the input).
let autocompleteItems = [];
let autocompleteActiveIndex = -1;
let autocompleteTimer = null;

function renderClubFinder() {
    clubFinder.innerHTML = `
        <h3>Find a club</h3>
        <form id="club-finder-form" autocomplete="off">
            <div class="club-finder-field">
                <input type="text" id="club-finder-input" placeholder="Enter an address" autocomplete="off"/>
                <ul id="club-finder-autocomplete" class="club-finder-autocomplete" hidden></ul>
            </div>
            <button type="submit">Search</button>
        </form>
    `;

    const form = document.getElementById('club-finder-form');
    const input = document.getElementById('club-finder-input');

    form.addEventListener('submit', event => {
        event.preventDefault();
        hideAutocomplete();
        const address = input.value.trim();
        if (address) {
            runClubFinderByAddress(address);
        }
    });

    input.addEventListener('input', () => {
        const query = input.value.trim();
        clearTimeout(autocompleteTimer);
        if (query.length < 3) {
            hideAutocomplete();
            return;
        }
        autocompleteTimer = setTimeout(() => fetchAutocomplete(query), 250);
    });

    input.addEventListener('keydown', event => {
        const listEl = document.getElementById('club-finder-autocomplete');
        if (listEl.hidden || autocompleteItems.length === 0) {
            return;
        }
        if (event.key === 'ArrowDown') {
            event.preventDefault();
            setAutocompleteActive(Math.min(autocompleteActiveIndex + 1, autocompleteItems.length - 1));
        } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            setAutocompleteActive(Math.max(autocompleteActiveIndex - 1, 0));
        } else if (event.key === 'Enter' && autocompleteActiveIndex >= 0) {
            event.preventDefault();
            selectAutocomplete(autocompleteItems[autocompleteActiveIndex]);
        } else if (event.key === 'Escape') {
            hideAutocomplete();
        }
    });

    // Any click outside the finder box dismisses the dropdown.
    document.addEventListener('pointerdown', event => {
        if (!clubFinder.contains(event.target)) {
            hideAutocomplete();
        }
    });
}

function fetchAutocomplete(query) {
    fetch(`/api/address-autocomplete?q=${encodeURIComponent(query)}`)
        .then(response => (response.ok ? response.json() : []))
        .then(renderAutocomplete)
        .catch(() => hideAutocomplete());
}

function renderAutocomplete(items) {
    const listEl = document.getElementById('club-finder-autocomplete');
    autocompleteItems = items || [];
    autocompleteActiveIndex = -1;

    if (autocompleteItems.length === 0) {
        hideAutocomplete();
        return;
    }

    listEl.innerHTML = autocompleteItems.map((item, index) => `
        <li class="club-finder-suggestion" data-index="${index}">${escapeHtml(item.label)}</li>
    `).join('');
    listEl.hidden = false;

    listEl.querySelectorAll('.club-finder-suggestion').forEach(el => {
        el.addEventListener('pointerdown', event => {
            // pointerdown (not click) so it fires before the input's blur.
            event.preventDefault();
            selectAutocomplete(autocompleteItems[parseInt(el.dataset.index, 10)]);
        });
    });
}

function setAutocompleteActive(index) {
    autocompleteActiveIndex = index;
    const listEl = document.getElementById('club-finder-autocomplete');
    listEl.querySelectorAll('.club-finder-suggestion').forEach((el, i) => {
        el.classList.toggle('active', i === index);
    });
}

function hideAutocomplete() {
    const listEl = document.getElementById('club-finder-autocomplete');
    if (listEl) {
        listEl.hidden = true;
        listEl.innerHTML = '';
    }
    autocompleteItems = [];
    autocompleteActiveIndex = -1;
}

function selectAutocomplete(item) {
    if (!item) {
        return;
    }
    const input = document.getElementById('club-finder-input');
    input.value = item.label;
    hideAutocomplete();
    runClubFinderByPoint(item.label, item.latitude, item.longitude);
}

function clearFinderResults() {
    if (addressMarker) {
        map.removeLayer(addressMarker);
        addressMarker = null;
    }
    if (suggestedClubIds.size > 0) {
        suggestedClubIds = new Set();
        refreshMarkerIcons();
    }
}

// Free-text address — geocoded server-side, so a bad address comes back 404.
function runClubFinderByAddress(address) {
    fetchClubSuggestions(`address=${encodeURIComponent(address)}`);
}

// A picked autocomplete suggestion already carries coordinates — no geocode.
function runClubFinderByPoint(label, latitude, longitude) {
    fetchClubSuggestions(`lat=${latitude}&lon=${longitude}&label=${encodeURIComponent(label)}`);
}

function fetchClubSuggestions(params) {
    suggestionPanel.hidden = false;
    suggestionPanelContent.innerHTML = '<p class="suggestion-status">Searching…</p>';

    const yearParam = currentYear != null ? `&year=${currentYear}` : '';
    fetch(`/api/club-suggestions?${params}${yearParam}`)
        .then(response => {
            if (response.status === 404) {
                throw new Error('not-found');
            }
            if (!response.ok) {
                throw new Error('failed');
            }
            return response.json();
        })
        .then(renderSuggestions)
        .catch(err => {
            suggestionPanelContent.innerHTML = err.message === 'not-found'
                ? '<p class="suggestion-status">Couldn\'t find that address. Try adding the town or postcode.</p>'
                : '<p class="suggestion-status">Something went wrong looking that up. Please try again.</p>';
        });
}

function renderSuggestions(data) {
    const resolved = data.resolved;
    const suggestions = data.suggestions || [];

    if (addressMarker) {
        map.removeLayer(addressMarker);
    }
    addressMarker = L.marker([resolved.latitude, resolved.longitude], {
        icon: L.divIcon({
            className: 'address-marker-icon',
            html: '<div class="address-marker-dot"></div>',
            iconSize: [22, 22],
            iconAnchor: [11, 11]
        }),
        zIndexOffset: 1000
    }).addTo(map);

    suggestedClubIds = new Set(suggestions.map(s => s.club.id));
    refreshMarkerIcons();

    // withinRange is null when the club has no level and no radius override —
    // "range not set", not a definite "out of range".
    function rangeBadgeHtml(withinRange) {
        if (withinRange === null || withinRange === undefined) {
            return '<span class="suggestion-badge range-unset">range not set</span>';
        }
        return withinRange
            ? '<span class="suggestion-badge in-range">in range</span>'
            : '<span class="suggestion-badge out-of-range">out of range</span>';
    }

    const items = suggestions.map((s, index) => `
        <li class="suggestion-item" data-club-id="${s.club.id}">
            <div class="suggestion-rank">${index + 1}</div>
            <div>
                <div class="suggestion-name">${escapeHtml(s.club.name)}</div>
                <div class="suggestion-meta">
                    ${escapeHtml(s.club.city)} · ${s.distanceKm} km
                    ${rangeBadgeHtml(s.withinRange)}
                </div>
            </div>
        </li>
    `).join('');

    suggestionPanelContent.innerHTML = `
        <h2>Clubs near this address</h2>
        <p class="suggestion-resolved">Showing results for:<br><strong>${escapeHtml(resolved.label)}</strong></p>
        <ol class="suggestion-list">${items || '<li class="suggestion-status">No clubs on file for this year.</li>'}</ol>
        <p class="suggestion-hint">Ranked by straight-line distance. &ldquo;In range&rdquo; means the address is
        inside the club&rsquo;s area of influence for ${currentYear}; &ldquo;range not set&rdquo; means the club has
        no level or radius assigned yet.</p>
    `;

    suggestionPanelContent.querySelectorAll('.suggestion-item').forEach(el => {
        el.addEventListener('click', () => {
            const clubId = parseInt(el.dataset.clubId, 10);
            const marker = allMarkers.find(m => m.club.id === clubId);
            if (marker) {
                suggestionPanel.hidden = true;
                openClubPanel(marker.club, marker);
            }
        });
    });

    map.panTo([resolved.latitude, resolved.longitude]);
}

suggestionPanelClose.addEventListener('click', () => {
    suggestionPanel.hidden = true;
    clearFinderResults();
});

Promise.all([
    fetch('/api/levels').then(response => response.json()),
    fetch('/api/years').then(response => response.json()),
    fetch('/api/clubs/history').then(response => response.json())
])
    .then(([levels, years, history]) => {
        selectedLevels = new Set(levels.map(level => level.level));

        // Filière options: every distinct value that appears in any year,
        // plus the "no filière" bucket. Fixed list, like levels — not
        // recomputed per year.
        const filieres = [...new Set(history.map(club => club.filiere).filter(Boolean))].sort();
        filieres.push(NO_FILIERE);
        selectedFilieres = new Set(filieres);

        renderLevelFilter(levels, filieres);
        renderClubFinder();

        history.forEach(club => {
            if (!clubHistoryByYear.has(club.year)) {
                clubHistoryByYear.set(club.year, []);
            }
            clubHistoryByYear.get(club.year).push(club);
        });
        recomputeCalibratedMax();

        if (years.length === 0) {
            return;
        }
        currentYear = years[years.length - 1];
        renderYearFilter(years);
        return loadClubsForYear(currentYear);
    })
    .catch(err => console.error('Failed to load clubs/levels/years', err));

// Rebuilds every marker's icon at the current zoom — also picks up changes
// to suggestedClubIds (the finder ring).
function refreshMarkerIcons() {
    const size = iconSizeForZoom(map.getZoom());
    allMarkers.forEach(marker => marker.setIcon(iconForClub(marker.club, size)));
}

map.on('zoomend', refreshMarkerIcons);
