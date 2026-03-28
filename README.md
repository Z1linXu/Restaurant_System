# Restaurant System MVP

Restaurant System is a web-first restaurant operations project focused on the MVP workflow:

POS -> Submit Order -> Kitchen -> Ready -> Complete

The current scope includes:
- Frontdesk/POS order workflow
- Kitchen Display System (KDS)
- Serving shelf / pickup handoff
- Frontdesk beverage workflow
- Snapshot-based order and item history
- Basic inventory deduction foundations
- WebSocket-based realtime operational updates

## Project Structure

```text
Restaurant_System/
  AGENTS.md
  backend/
  doc/
  frontend/
```

## Backend Stack

- Spring Boot 3
- PostgreSQL
- JPA/Hibernate
- MyBatis-Plus
- Spring WebSocket + STOMP

## Frontend Direction

- React
- Touch-friendly POS / KDS / shelf screens
- Admin pages later

## Local Development

Backend configuration is split into:
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-local.yml`
- `backend/src/main/resources/application-example.yml`

Run the backend from `backend/`:

```bash
mvn spring-boot:run
```

## Notes

- The repository currently uses `doc/` for project documentation.
- `application-local.yml` is intended for local-only secrets.
- MVP prioritizes business flow correctness and operational simplicity.
