# Phoenix Portal Deployment

Simple deployment using Railway (backend + database) and Vercel (frontend).

## Backend (Railway)

1. Create a new Railway project
2. Add a **PostgreSQL** database service
3. Add a new service from GitHub, point to `portal/apps/backend`
4. **Link the PostgreSQL to your backend service** (this auto-injects PGHOST, PGUSER, etc.)
5. Add this environment variable manually:
   ```
   JWT_SECRET=<generate a random 32+ character string>
   ```
6. Railway will auto-detect the Dockerfile and deploy

**Note:** Railway automatically provides `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD` when you link a PostgreSQL service. You don't need to set these manually.

## Frontend (Vercel)

1. Import your repo to Vercel
2. Set the root directory to `portal/apps/web`
3. Set environment variable:
   ```
   NEXT_PUBLIC_API_URL=https://your-railway-app.railway.app
   ```
4. Deploy

## Local Development

```bash
# Start PostgreSQL (use Docker or local install)
docker run -d --name phoenix-db -p 5432:5432 \
  -e POSTGRES_DB=phoenix_portal \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  postgres:15

# Backend
cd portal/apps/backend
./gradlew run

# Frontend (in another terminal)
cd portal/apps/web
npm run dev
```

## Environment Variables

### Backend (Ktor)
| Variable | Description | Default |
|----------|-------------|---------|
| PORT | Server port | 8080 |
| PGHOST | PostgreSQL host | localhost |
| PGPORT | PostgreSQL port | 5432 |
| PGDATABASE | Database name | phoenix_portal |
| PGUSER | Database username | postgres |
| PGPASSWORD | Database password | postgres |
| JWT_SECRET | JWT signing secret | dev-secret (change in prod!) |

### Frontend (Next.js)
| Variable | Description | Default |
|----------|-------------|---------|
| NEXT_PUBLIC_API_URL | Backend API URL | http://localhost:8080 |
