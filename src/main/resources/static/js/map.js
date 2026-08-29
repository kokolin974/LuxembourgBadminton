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

function hideOtherMarkers(selectedMarker) {
    allMarkers.forEach(marker => {
        if (marker !== selectedMarker && map.hasLayer(marker)) {
            map.removeLayer(marker);
        }
    });
}

function showAllMarkers() {
    allMarkers.forEach(marker => {
        if (!map.hasLayer(marker)) {
            marker.addTo(map);
        }
    });
}

function openClubPanel(club, marker) {
    panelContent.innerHTML = `
        <div class="club-house">
            <h2>${escapeHtml(club.name)} — ${escapeHtml(club.city)}</h2>
            <div class="club-house-wrap">
                <img class="club-house-img" src="/img/discovery_house_clean.svg" alt="Club levels diagram"/>
                ${levelOverlaysHtml(club)}
            </div>
        </div>
    `;
    panel.hidden = false;

    hideOtherMarkers(marker);
}

panelClose.addEventListener('click', () => {
    panel.hidden = true;
    showAllMarkers();
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

fetch('/api/clubs')
    .then(response => response.json())
    .then(clubs => {
        const size = iconSizeForZoom(map.getZoom());
        allMarkers = spreadOverlappingClubs(clubs).map(({ club, lat, lng }) => {
            const marker = L.marker([lat, lng], { icon: iconForClub(club, size) }).addTo(map);
            marker.club = club;
            marker.on('click', () => openClubPanel(club, marker));
            return marker;
        });
    })
    .catch(err => console.error('Failed to load clubs', err));

map.on('zoomend', () => {
    const size = iconSizeForZoom(map.getZoom());
    allMarkers.forEach(marker => marker.setIcon(iconForClub(marker.club, size)));
});
