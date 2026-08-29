const LUXEMBOURG_CENTER = [49.8153, 6.1296];

const map = L.map('map').setView(LUXEMBOURG_CENTER, 9);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

// Marker icon reflects the club's level (clubLevel1.png..clubLevel7.png); falls
// back to the plain house icon if the club has no level or its image fails to load.
const DEFAULT_ICON_URL = '/img/house-marker.svg';

// Icon shrinks as you zoom in — at country-wide zoom a bigger icon stays
// visible, but once you're zoomed into a small area a 40px house photo per
// marker gets overwhelming, especially where several markers sit close
// together. Tuned in bands rather than a continuous formula for predictability.
function iconSizeForZoom(zoom) {
    if (zoom <= 10) return 40;
    if (zoom <= 12) return 32;
    if (zoom <= 14) return 26;
    if (zoom <= 16) return 22;
    return 18;
}

function iconForClub(club, size) {
    const url = club.level ? `/img/clubLevel${club.level.level}.png` : DEFAULT_ICON_URL;
    return L.divIcon({
        html: `<img class="club-marker-img" src="${url}" onerror="this.src='${DEFAULT_ICON_URL}'"/>`,
        className: 'club-marker-icon',
        iconSize: [size, size],
        iconAnchor: [size / 2, size],
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
    { field: 'communication', cssClass: 'ov-communication' },
    { field: 'finance', cssClass: 'ov-finance' },
    { field: 'humanResources', cssClass: 'ov-human-resources' },
    { field: 'clubActivities', cssClass: 'ov-club-activities' },
    { field: 'membership', cssClass: 'ov-membership' }
];

function levelOverlaysHtml(club) {
    return LEVELS.map(level => {
        const text = club[level.field];
        const escaped = text ? escapeHtml(text) : '';
        return `<div class="club-overlay ${level.cssClass}" title="${escaped}">${escaped}</div>`;
    }).join('');
}

const panel = document.getElementById('club-panel');
const panelContent = document.getElementById('club-panel-content');
const panelClose = document.getElementById('club-panel-close');

let allMarkers = [];

// Levels currently checked in the filter box. A marker is only ever on the
// map if its club's level is in this set — clicking a marker to isolate it
// temporarily overrides that (hiding the rest), but closing the panel goes
// back through applyFilter() rather than blindly re-showing everyone, so
// filtered-out clubs stay hidden.
let selectedLevels = new Set();

function markerPassesFilter(marker) {
    const level = marker.club.level;
    return !level || selectedLevels.has(level.level);
}

function applyFilter() {
    allMarkers.forEach(marker => {
        const shouldShow = markerPassesFilter(marker);
        const isShown = map.hasLayer(marker);
        if (shouldShow && !isShown) {
            marker.addTo(map);
        } else if (!shouldShow && isShown) {
            map.removeLayer(marker);
        }
    });
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

function radiusControlHtml(club) {
    const levelDefault = club.level ? club.level.radiusKm : null;
    const current = club.effectiveRadiusKm ?? levelDefault ?? RADIUS_MIN_KM;
    const hasOverride = club.radiusOverrideKm != null;
    return `
        <div class="club-radius">
            <label for="club-radius-slider">Radius of influence: <span id="club-radius-value">${current}</span> km</label>
            <input type="range" id="club-radius-slider" min="${RADIUS_MIN_KM}" max="${RADIUS_MAX_KM}"
                   step="${RADIUS_STEP_KM}" value="${current}"/>
            <button type="button" id="club-radius-reset" class="club-radius-reset" ${hasOverride ? '' : 'hidden'}>
                Reset to level default (${levelDefault ?? '—'} km)
            </button>
        </div>
    `;
}

function saveRadius(club, radiusOverrideKm) {
    return fetch(`/api/clubs/${club.id}/radius`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ radiusOverrideKm })
    })
        .then(response => response.json())
        .then(updated => {
            club.radiusOverrideKm = updated.radiusOverrideKm;
            club.effectiveRadiusKm = updated.effectiveRadiusKm;
        })
        .catch(err => console.error('Failed to save radius', err));
}

function wireRadiusControl(club) {
    const slider = document.getElementById('club-radius-slider');
    const valueLabel = document.getElementById('club-radius-value');
    const resetButton = document.getElementById('club-radius-reset');

    slider.addEventListener('input', () => {
        valueLabel.textContent = slider.value;
    });

    slider.addEventListener('change', () => {
        const value = parseFloat(slider.value);
        saveRadius(club, value).then(() => {
            resetButton.hidden = false;
        });
    });

    resetButton.addEventListener('click', () => {
        saveRadius(club, null).then(() => {
            const levelDefault = club.level ? club.level.radiusKm : RADIUS_MIN_KM;
            slider.value = levelDefault;
            valueLabel.textContent = levelDefault;
            resetButton.hidden = true;
        });
    });
}

function openClubPanel(club, marker) {
    panelContent.innerHTML = `
        <div class="club-house">
            <h2>${escapeHtml(club.name)} — ${escapeHtml(club.city)}</h2>
            ${radiusControlHtml(club)}
            <div class="club-house-wrap">
                <img class="club-house-img" src="/img/discovery_house_clean.svg" alt="Club levels diagram"/>
                ${levelOverlaysHtml(club)}
            </div>
        </div>
    `;
    panel.hidden = false;

    wireRadiusControl(club);
    hideOtherMarkers(marker);
}

panelClose.addEventListener('click', () => {
    panel.hidden = true;
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

function renderLevelFilter(levels) {
    const checkboxes = levels.map(level => `
        <label>
            <input type="checkbox" value="${level.level}" checked/>
            ${level.level} - ${escapeHtml(level.label)}
        </label>
    `).join('');
    levelFilter.innerHTML = `
        <h3>Filter by level</h3>
        ${checkboxes}
        <div class="level-filter-actions">
            <button type="button" id="level-filter-all">All</button>
            <button type="button" id="level-filter-none">None</button>
        </div>
    `;

    levelFilter.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
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

    document.getElementById('level-filter-all').addEventListener('click', () => {
        selectedLevels = new Set(levels.map(level => level.level));
        levelFilter.querySelectorAll('input[type="checkbox"]').forEach(cb => { cb.checked = true; });
        applyFilter();
    });

    document.getElementById('level-filter-none').addEventListener('click', () => {
        selectedLevels = new Set();
        levelFilter.querySelectorAll('input[type="checkbox"]').forEach(cb => { cb.checked = false; });
        applyFilter();
    });
}

Promise.all([
    fetch('/api/levels').then(response => response.json()),
    fetch('/api/clubs').then(response => response.json())
])
    .then(([levels, clubs]) => {
        selectedLevels = new Set(levels.map(level => level.level));
        renderLevelFilter(levels);

        const size = iconSizeForZoom(map.getZoom());
        allMarkers = spreadOverlappingClubs(clubs).map(({ club, lat, lng }) => {
            const marker = L.marker([lat, lng], { icon: iconForClub(club, size) }).addTo(map);
            marker.club = club;
            marker.on('click', () => openClubPanel(club, marker));
            return marker;
        });
    })
    .catch(err => console.error('Failed to load clubs/levels', err));

map.on('zoomend', () => {
    const size = iconSizeForZoom(map.getZoom());
    allMarkers.forEach(marker => marker.setIcon(iconForClub(marker.club, size)));
});
