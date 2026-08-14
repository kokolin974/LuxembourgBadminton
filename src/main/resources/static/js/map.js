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

// Each floor of the house cross-section maps to a slice of the club's info.
function floorsFor(club) {
    const contactLines = [];
    if (club.website) {
        contactLines.push(`<a href="${escapeHtml(club.website)}" target="_blank" rel="noopener">Website</a>`);
    }
    if (club.contactEmail) {
        contactLines.push(`<a href="mailto:${escapeHtml(club.contactEmail)}">${escapeHtml(club.contactEmail)}</a>`);
    }

    return {
        roof: {
            title: 'Club',
            html: `<p>${escapeHtml(club.name)}</p><p>${escapeHtml(club.city)}</p>`
        },
        upper: {
            title: 'About',
            html: `<p>${escapeHtml(club.description) || 'No description yet.'}</p>`
        },
        ground: {
            title: 'Contact',
            html: contactLines.length ? contactLines.map(l => `<p>${l}</p>`).join('') : '<p>No contact info yet.</p>'
        }
    };
}

const HOUSE_SVG = `
    <svg viewBox="0 0 200 220" xmlns="http://www.w3.org/2000/svg">
        <polygon class="floor" data-floor="roof" points="20,80 100,15 180,80" />
        <rect class="floor" data-floor="upper" x="30" y="80" width="140" height="65" />
        <rect class="floor" data-floor="ground" x="30" y="145" width="140" height="65" />
        <text class="floor-label" x="100" y="55" text-anchor="middle">Roof</text>
        <text class="floor-label" x="100" y="116" text-anchor="middle">Upper floor</text>
        <text class="floor-label" x="100" y="181" text-anchor="middle">Ground floor</text>
    </svg>
`;

const panel = document.getElementById('club-panel');
const panelContent = document.getElementById('club-panel-content');
const panelClose = document.getElementById('club-panel-close');

function showFloor(floors, floorKey, svgRoot, infoBox) {
    svgRoot.querySelectorAll('.floor').forEach(el => {
        el.classList.toggle('active', el.dataset.floor === floorKey);
    });
    const floor = floors[floorKey];
    infoBox.innerHTML = `<h3>${floor.title}</h3>${floor.html}`;
}

function openClubPanel(club) {
    const floors = floorsFor(club);

    panelContent.innerHTML = `
        <div class="club-house">
            <h2>${escapeHtml(club.name)}</h2>
            ${HOUSE_SVG}
            <div class="club-floor-info" id="club-floor-info"></div>
            <p class="club-floor-hint">Click a level of the house for more.</p>
        </div>
    `;

    const svgRoot = panelContent.querySelector('svg');
    const infoBox = panelContent.querySelector('#club-floor-info');

    svgRoot.querySelectorAll('.floor').forEach(el => {
        el.addEventListener('click', () => showFloor(floors, el.dataset.floor, svgRoot, infoBox));
    });

    showFloor(floors, 'roof', svgRoot, infoBox);

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
