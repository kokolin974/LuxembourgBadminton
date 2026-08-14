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

function popupHtml(club) {
    const website = club.website
        ? `<p><a href="${club.website}" target="_blank" rel="noopener">Website</a></p>`
        : '';
    const email = club.contactEmail
        ? `<p><a href="mailto:${club.contactEmail}">${club.contactEmail}</a></p>`
        : '';
    return `
        <div class="club-popup">
            <h3>${club.name}</h3>
            <p>${club.city}</p>
            <p>${club.description ?? ''}</p>
            ${website}
            ${email}
        </div>
    `;
}

fetch('/api/clubs')
    .then(response => response.json())
    .then(clubs => {
        clubs.forEach(club => {
            L.marker([club.latitude, club.longitude], { icon: houseIcon })
                .addTo(map)
                .bindPopup(popupHtml(club));
        });
    })
    .catch(err => console.error('Failed to load clubs', err));
