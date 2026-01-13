# Production deployment with GHCR images

## What your friend needs to run the microservices

### 1. Prerequisites
- Docker and Docker Compose installed
- Git (optional, only if cloning)

---

### 2. Clone or download the compose file
Your friend needs **only one file**: `docker-compose.ghcr.yml` and `.env.example` from this repo.

Option A: Clone the repo
```bash
git clone https://github.com/IMedet/gateway-service.git
cd gateway-service
```

Option B: Download just the files
- Download `docker-compose.ghcr.yml` and `.env.example` from the repo
- Put them in a folder

---

### 3. Configure environment
Copy `.env.example` to `.env` and edit if needed:
```bash
cp .env.example .env
```

Edit `.env`:
```env
GITHUB_OWNER=IMedet
INTERNAL_GATEWAY_SECRET=766SBSZ9soKRmJISfScxyGLDzht
```

> **Important**: `GITHUB_OWNER` must match the GitHub owner of the images (your username or org).

---

### 4. Start all services
```bash
docker-compose -f docker-compose.ghcr.yml up -d
```

---

### 5. Verify
- Eureka: http://localhost:8761
- Gateway: http://localhost:8888
- Auth Service (via gateway): http://localhost:8888/auth/**
- Event Service (via gateway): http://localhost:8888/events/**
- Administration (via gateway): http://localhost:8888/admin/**

---

### 6. Stop
```bash
docker-compose -f docker-compose.ghcr.yml down
```

---

## What’s inside
All images are pulled from GitHub Container Registry (GHCR):
- `ghcr.io/IMedet/eureka:latest`
- `ghcr.io/IMedet/auth-service:latest`
- `ghcr.io/IMedet/event-service:latest`
- `ghcr.io/IMedet/administration:latest`
- `ghcr.io/IMedet/gateway-service:latest`

All services are configured with:
- Eureka service discovery
- Internal gateway HMAC authentication
- Environment variables for service URLs

---

## Troubleshooting
- If images not found: check `GITHUB_OWNER` in `.env`
- If services fail to start: check Docker logs with `docker-compose -f docker-compose.ghcr.yml logs <service>`
- If gateway returns 403/500: ensure `INTERNAL_GATEWAY_SECRET` matches across all services

---

## Security notes
- The compose uses the same HMAC secret for internal gateway authentication
- All services are exposed on localhost only (no external access)
- JWT authentication is handled by the gateway and forwarded to services
