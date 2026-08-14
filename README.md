# Badminton Map

Interactive map of badminton clubs in Luxembourg. Spring Boot + Thymeleaf + Leaflet.js.

## Run locally (no Docker)

```
./mvnw spring-boot:run
```

Uses the `dev` profile (H2 file database, auto-seeded with sample clubs). Visit http://localhost:8080.

## Run with Docker Compose

```
docker compose up --build
```

Uses the `docker` profile (Postgres). Visit http://localhost:8080.

## API

- `GET /api/clubs` — list all clubs
- `GET /api/clubs/{id}` — get one club
- `POST /api/clubs` — create a club
- `PUT /api/clubs/{id}` — update a club
- `DELETE /api/clubs/{id}` — delete a club
