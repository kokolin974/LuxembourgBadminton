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

// Levels of the club house diagram (discovery_house_clean.svg), roof to foundation —
// each maps to one Club field and keeps the same color coding as the image.
const LEVELS = [
    { field: 'visionStrategy', cssClass: 'level-vision-strategy', title: 'Vision & Strategy' },
    { field: 'governance', cssClass: 'level-governance', title: 'Governance' },
    { field: 'communication', cssClass: 'level-communication', title: 'Communication' },
    { field: 'finance', cssClass: 'level-finance', title: 'Finance' },
    { field: 'humanResources', cssClass: 'level-human-resources', title: 'Human Resources' },
    { field: 'clubActivities', cssClass: 'level-club-activities', title: 'Club Activities' },
    { field: 'membership', cssClass: 'level-membership', title: 'Membership' }
];

function levelsHtml(club) {
    return LEVELS.map(level => {
        const text = club[level.field];
        return `
            <div class="club-level ${level.cssClass}">
                <h3>${level.title}</h3>
                <p>${text ? escapeHtml(text) : '—'}</p>
            </div>
        `;
    }).join('');
}

const panel = document.getElementById('club-panel');
const panelContent = document.getElementById('club-panel-content');
const panelClose = document.getElementById('club-panel-close');

function openClubPanel(club) {
    panelContent.innerHTML = `
        <div class="club-house">
            <h2>${escapeHtml(club.name)} — ${escapeHtml(club.city)}</h2>
            <img class="club-house-img" src="/img/discovery_house_clean.svg" alt="Club levels diagram"/>
            ${levelsHtml(club)}
        </div>
    `;
    panel.hidden = false;
}

panelClose.addEventListener('click', () => {
    panel.hidden = true;
});

fetch('/api/clubs')
    .then(response => response.json())
    .then(clubs => {
        clubs.forEach(club => {
            L.marker([club.latitude, club.longitude], { icon: houseIcon })
                .addTo(map)
                .on('click', () => openClubPanel(club));
        });
    })
    .catch(err => console.error('Failed to load clubs', err));
