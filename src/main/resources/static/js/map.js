const LUXEMBOURG_CENTER = [49.8153, 6.1296];

const map = L.map('map').setView(LUXEMBOURG_CENTER, 9);

L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

const houseIcon = L.icon({
    iconUrl: '/img/house-marker.svg',
    iconSize: [32, 32],
    iconAnchor: [16, 32],
    popupAnchor: [0, -28]
});

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

// Fixed for now — every club gets the same catchment radius. The plan is to
// replace this with a per-club value (based on club size/other factors) once
// that's modeled, at which point it'll come from the club object instead.
const CLUB_RADIUS_METERS = 10000;

let allMarkers = [];
let influenceCircle = null;

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

function openClubPanel(club, lat, lng, marker) {
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

    if (influenceCircle) {
        map.removeLayer(influenceCircle);
    }
    influenceCircle = L.circle([lat, lng], {
        radius: CLUB_RADIUS_METERS,
        color: '#1f6f4d',
        weight: 2,
        fillOpacity: 0.08
    }).addTo(map);
}

panelClose.addEventListener('click', () => {
    panel.hidden = true;
    showAllMarkers();
    if (influenceCircle) {
        map.removeLayer(influenceCircle);
        influenceCircle = null;
    }
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
        const radiusDegrees = 0.0015;
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
        allMarkers = spreadOverlappingClubs(clubs).map(({ club, lat, lng }) => {
            const marker = L.marker([lat, lng], { icon: houseIcon }).addTo(map);
            marker.on('click', () => openClubPanel(club, lat, lng, marker));
            return marker;
        });
    })
    .catch(err => console.error('Failed to load clubs', err));
