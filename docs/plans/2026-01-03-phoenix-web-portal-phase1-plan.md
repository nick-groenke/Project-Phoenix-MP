# Phoenix Web Portal - Phase 1: Foundation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Establish the foundational infrastructure for the Phoenix Web Portal: Ktor backend, Supabase schema, auth flow, and basic sync endpoint.

**Architecture:** Monorepo with Next.js frontend and Ktor backend. Ktor imports the existing KMP shared module for model reuse. Supabase provides PostgreSQL with RLS and authentication. Azure Container Apps for deployment.

**Tech Stack:** Ktor 2.x, Supabase (PostgreSQL + Auth), Next.js 14, TypeScript, Kotlin, Azure Container Apps

**Design Reference:** `docs/plans/2026-01-03-phoenix-web-portal-design.md`

---

## Prerequisites

Before starting, ensure you have:
- Node.js 20+ installed
- JDK 17+ installed
- Supabase CLI installed (`npm install -g supabase`)
- Azure CLI installed and authenticated
- Access to the existing Supabase project (check `shared/.../AppConfig.kt` for URL)

---

## Task 1: Create Monorepo Structure

**Files:**
- Create: `portal/` directory structure
- Create: `portal/package.json` (workspace root)
- Create: `portal/.gitignore`

**Step 1: Create the portal directory structure**

```bash
mkdir -p portal/apps/web
mkdir -p portal/apps/backend
mkdir -p portal/packages/shared-types
mkdir -p portal/supabase/migrations
```

**Step 2: Create root package.json for npm workspaces**

Create `portal/package.json`:
```json
{
  "name": "phoenix-portal",
  "version": "0.1.0",
  "private": true,
  "workspaces": [
    "apps/*",
    "packages/*"
  ],
  "scripts": {
    "dev:web": "npm run dev -w apps/web",
    "dev:backend": "cd apps/backend && ./gradlew run",
    "build:web": "npm run build -w apps/web",
    "build:backend": "cd apps/backend && ./gradlew build",
    "db:migrate": "supabase db push",
    "db:reset": "supabase db reset",
    "typecheck": "npm run typecheck -w apps/web"
  }
}
```

**Step 3: Create .gitignore**

Create `portal/.gitignore`:
```gitignore
# Dependencies
node_modules/
.pnpm-store/

# Build outputs
.next/
out/
dist/
build/

# Environment
.env
.env.local
.env.*.local

# IDE
.idea/
*.iml
.vscode/

# Kotlin/Gradle
.gradle/
apps/backend/build/

# Supabase
.supabase/

# OS
.DS_Store
Thumbs.db
```

**Step 4: Commit**

```bash
git add portal/
git commit -m "chore: initialize portal monorepo structure"
```

---

## Task 2: Set Up Next.js Frontend

**Files:**
- Create: `portal/apps/web/` (Next.js project)
- Create: `portal/apps/web/package.json`
- Create: `portal/apps/web/next.config.js`
- Create: `portal/apps/web/tsconfig.json`
- Create: `portal/apps/web/tailwind.config.ts`
- Create: `portal/apps/web/src/app/layout.tsx`
- Create: `portal/apps/web/src/app/page.tsx`

**Step 1: Create package.json for web app**

Create `portal/apps/web/package.json`:
```json
{
  "name": "web",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "next lint",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "next": "^14.2.0",
    "react": "^18.3.0",
    "react-dom": "^18.3.0",
    "@supabase/supabase-js": "^2.45.0",
    "@supabase/ssr": "^0.5.0"
  },
  "devDependencies": {
    "@types/node": "^20.0.0",
    "@types/react": "^18.3.0",
    "@types/react-dom": "^18.3.0",
    "autoprefixer": "^10.4.0",
    "postcss": "^8.4.0",
    "tailwindcss": "^3.4.0",
    "typescript": "^5.4.0"
  }
}
```

**Step 2: Create next.config.js**

Create `portal/apps/web/next.config.js`:
```javascript
/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  experimental: {
    serverActions: {
      bodySizeLimit: '2mb',
    },
  },
}

module.exports = nextConfig
```

**Step 3: Create tsconfig.json**

Create `portal/apps/web/tsconfig.json`:
```json
{
  "compilerOptions": {
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
```

**Step 4: Create Tailwind config**

Create `portal/apps/web/tailwind.config.ts`:
```typescript
import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        phoenix: {
          50: '#fef3e2',
          100: '#fde4b9',
          200: '#fcd38c',
          300: '#fbc15e',
          400: '#fab43b',
          500: '#f9a825',
          600: '#f59322',
          700: '#ef7b1e',
          800: '#e9641b',
          900: '#df4016',
        },
      },
    },
  },
  plugins: [],
}

export default config
```

**Step 5: Create PostCSS config**

Create `portal/apps/web/postcss.config.js`:
```javascript
module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
}
```

**Step 6: Create globals.css**

Create `portal/apps/web/src/app/globals.css`:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

:root {
  --foreground-rgb: 0, 0, 0;
  --background-rgb: 255, 255, 255;
}

@media (prefers-color-scheme: dark) {
  :root {
    --foreground-rgb: 255, 255, 255;
    --background-rgb: 10, 10, 10;
  }
}

body {
  color: rgb(var(--foreground-rgb));
  background: rgb(var(--background-rgb));
}
```

**Step 7: Create root layout**

Create `portal/apps/web/src/app/layout.tsx`:
```typescript
import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: 'Phoenix Portal',
  description: 'Premium analytics for Vitruvian Trainer',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en">
      <body className={inter.className}>{children}</body>
    </html>
  )
}
```

**Step 8: Create home page placeholder**

Create `portal/apps/web/src/app/page.tsx`:
```typescript
export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-24">
      <h1 className="text-4xl font-bold text-phoenix-500">
        Phoenix Portal
      </h1>
      <p className="mt-4 text-gray-600 dark:text-gray-400">
        Premium analytics coming soon
      </p>
    </main>
  )
}
```

**Step 9: Create next-env.d.ts**

Create `portal/apps/web/next-env.d.ts`:
```typescript
/// <reference types="next" />
/// <reference types="next/image-types/global" />

// NOTE: This file should not be edited
// see https://nextjs.org/docs/basic-features/typescript for more information.
```

**Step 10: Install dependencies and verify**

Run from `portal/`:
```bash
npm install
npm run dev:web
```

Expected: Dev server starts at http://localhost:3000, shows "Phoenix Portal" heading

**Step 11: Commit**

```bash
git add portal/apps/web/
git commit -m "feat(portal): add Next.js frontend scaffold"
```

---

## Task 3: Set Up Ktor Backend

**Files:**
- Create: `portal/apps/backend/build.gradle.kts`
- Create: `portal/apps/backend/settings.gradle.kts`
- Create: `portal/apps/backend/gradle.properties`
- Create: `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/Application.kt`
- Create: `portal/apps/backend/src/main/resources/application.conf`

**Step 1: Create settings.gradle.kts**

Create `portal/apps/backend/settings.gradle.kts`:
```kotlin
rootProject.name = "phoenix-portal-backend"

// Include the shared module from the main project
includeBuild("../../../") {
    dependencySubstitution {
        substitute(module("com.devil.phoenixproject:shared")).using(project(":shared"))
    }
}
```

**Step 2: Create gradle.properties**

Create `portal/apps/backend/gradle.properties`:
```properties
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

**Step 3: Create build.gradle.kts**

Create `portal/apps/backend/build.gradle.kts`:
```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("io.ktor.plugin") version "2.3.12"
    application
}

group = "com.devil.phoenixproject.portal"
version = "0.1.0"

application {
    mainClass.set("com.devil.phoenixproject.portal.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.12")
    implementation("io.ktor:ktor-server-cors-jvm:2.3.12")
    implementation("io.ktor:ktor-server-auth-jvm:2.3.12")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.3.12")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.12")
    implementation("io.ktor:ktor-server-call-logging-jvm:2.3.12")

    // Ktor Client (for Supabase calls)
    implementation("io.ktor:ktor-client-core-jvm:2.3.12")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:2.3.12")

    // Kotlinx
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Database
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.8")

    // Shared module from KMP project (domain models)
    // Note: This requires the KMP shared module to be JVM-compatible
    // For now, we'll duplicate essential models

    // Testing
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

kotlin {
    jvmToolchain(17)
}
```

**Step 4: Create application.conf**

Create `portal/apps/backend/src/main/resources/application.conf`:
```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.devil.phoenixproject.portal.ApplicationKt.module ]
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/phoenix"
    url = ${?DATABASE_URL}
    driver = "org.postgresql.Driver"
    user = "postgres"
    user = ${?DATABASE_USER}
    password = ""
    password = ${?DATABASE_PASSWORD}
}

supabase {
    url = ${?SUPABASE_URL}
    anonKey = ${?SUPABASE_ANON_KEY}
    serviceKey = ${?SUPABASE_SERVICE_KEY}
    jwtSecret = ${?SUPABASE_JWT_SECRET}
}
```

**Step 5: Create logback.xml**

Create `portal/apps/backend/src/main/resources/logback.xml`:
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{YYYY-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
    <logger name="io.ktor" level="INFO"/>
    <logger name="com.devil.phoenixproject" level="DEBUG"/>
</configuration>
```

**Step 6: Create Application.kt**

Create `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/Application.kt`:
```kotlin
package com.devil.phoenixproject.portal

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost() // Configure properly for production
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: ${cause.localizedMessage}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    routing {
        get("/") {
            call.respondText("Phoenix Portal API v0.1.0")
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy"))
        }
    }
}
```

**Step 7: Create Gradle wrapper**

Run from `portal/apps/backend/`:
```bash
gradle wrapper --gradle-version 8.5
```

**Step 8: Verify build**

Run from `portal/apps/backend/`:
```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

**Step 9: Verify run**

Run from `portal/apps/backend/`:
```bash
./gradlew run
```

Expected: Server starts at http://localhost:8080, `/health` returns `{"status":"healthy"}`

**Step 10: Commit**

```bash
git add portal/apps/backend/
git commit -m "feat(portal): add Ktor backend scaffold"
```

---

## Task 4: Create Supabase Schema Migrations

**Files:**
- Create: `portal/supabase/config.toml`
- Create: `portal/supabase/migrations/00001_initial_schema.sql`

**Step 1: Create Supabase config**

Create `portal/supabase/config.toml`:
```toml
[api]
enabled = true
port = 54321
schemas = ["public"]
extra_search_path = ["public", "extensions"]
max_rows = 1000

[db]
port = 54322
major_version = 15

[studio]
enabled = true
port = 54323

[auth]
enabled = true
site_url = "http://localhost:3000"
additional_redirect_urls = ["http://localhost:3000/auth/callback"]
jwt_expiry = 3600
enable_signup = true
```

**Step 2: Create initial migration**

Create `portal/supabase/migrations/00001_initial_schema.sql`:
```sql
-- ==================== PROFILES ====================
-- Extends Supabase auth.users

CREATE TABLE public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT,
    avatar_url TEXT,
    is_premium BOOLEAN DEFAULT FALSE,
    subscription_status TEXT DEFAULT 'free',
    subscription_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_synced_at TIMESTAMPTZ
);

-- Create profile automatically when user signs up
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (NEW.id, NEW.raw_user_meta_data->>'display_name');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ==================== SYNC INFRASTRUCTURE ====================

CREATE TABLE public.sync_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    device_name TEXT,
    platform TEXT,
    last_sync_cursor TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

-- ==================== WORKOUT DATA ====================

CREATE TABLE public.workout_sessions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    -- Core fields
    timestamp BIGINT NOT NULL,
    mode TEXT NOT NULL,
    target_reps INTEGER NOT NULL,
    weight_per_cable_kg REAL NOT NULL,
    progression_kg REAL DEFAULT 0.0,
    duration BIGINT DEFAULT 0,
    total_reps INTEGER DEFAULT 0,
    warmup_reps INTEGER DEFAULT 0,
    working_reps INTEGER DEFAULT 0,
    is_just_lift BOOLEAN DEFAULT FALSE,
    stop_at_top BOOLEAN DEFAULT FALSE,
    eccentric_load INTEGER DEFAULT 100,
    echo_level INTEGER DEFAULT 1,
    exercise_id TEXT,
    exercise_name TEXT,
    routine_session_id TEXT,
    routine_name TEXT,
    -- Safety tracking
    safety_flags INTEGER DEFAULT 0,
    deload_warning_count INTEGER DEFAULT 0,
    rom_violation_count INTEGER DEFAULT 0,
    spotter_activations INTEGER DEFAULT 0,
    -- Set Summary Metrics
    peak_force_concentric_a REAL,
    peak_force_concentric_b REAL,
    peak_force_eccentric_a REAL,
    peak_force_eccentric_b REAL,
    avg_force_concentric_a REAL,
    avg_force_concentric_b REAL,
    avg_force_eccentric_a REAL,
    avg_force_eccentric_b REAL,
    heaviest_lift_kg REAL,
    total_volume_kg REAL,
    estimated_calories REAL,
    warmup_avg_weight_kg REAL,
    working_avg_weight_kg REAL,
    burnout_avg_weight_kg REAL,
    peak_weight_kg REAL,
    rpe INTEGER,
    -- Sync metadata
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version INTEGER DEFAULT 1
);

CREATE INDEX idx_workout_sessions_user ON public.workout_sessions(user_id);
CREATE INDEX idx_workout_sessions_timestamp ON public.workout_sessions(timestamp DESC);
CREATE INDEX idx_workout_sessions_exercise ON public.workout_sessions(exercise_id);

-- ==================== SESSION SUMMARIES ====================

CREATE TABLE public.session_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES public.workout_sessions(id) ON DELETE CASCADE,
    rep_velocities JSONB,
    rep_powers JSONB,
    rep_tut_ms JSONB,
    sticking_point_position_mm REAL,
    sticking_point_min_velocity REAL,
    left_right_imbalance_percent REAL,
    position_force_bins JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_session_summaries_session ON public.session_summaries(session_id);

-- ==================== PERSONAL RECORDS ====================

CREATE TABLE public.personal_records (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    exercise_id TEXT NOT NULL,
    exercise_name TEXT NOT NULL,
    weight REAL NOT NULL,
    reps INTEGER NOT NULL,
    one_rep_max REAL NOT NULL,
    achieved_at BIGINT NOT NULL,
    workout_mode TEXT NOT NULL,
    pr_type TEXT NOT NULL DEFAULT 'MAX_WEIGHT',
    volume REAL DEFAULT 0.0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, exercise_id, workout_mode, pr_type)
);

CREATE INDEX idx_personal_records_user ON public.personal_records(user_id);
CREATE INDEX idx_personal_records_exercise ON public.personal_records(exercise_id);

-- ==================== ROUTINES ====================

CREATE TABLE public.routines (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    created_at_local BIGINT NOT NULL,
    last_used BIGINT,
    use_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    version INTEGER DEFAULT 1
);

CREATE INDEX idx_routines_user ON public.routines(user_id);

CREATE TABLE public.routine_documents (
    routine_id UUID PRIMARY KEY REFERENCES public.routines(id) ON DELETE CASCADE,
    exercises_json JSONB NOT NULL,
    supersets_json JSONB,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    version INTEGER DEFAULT 1
);

-- ==================== WEEKLY SUMMARIES ====================

CREATE TABLE public.weekly_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    total_volume_kg REAL DEFAULT 0,
    total_reps INTEGER DEFAULT 0,
    workout_count INTEGER DEFAULT 0,
    avg_intensity REAL,
    muscle_group_distribution JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, week_start)
);

CREATE INDEX idx_weekly_summaries_user ON public.weekly_summaries(user_id);

-- ==================== ROW LEVEL SECURITY ====================

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.workout_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.session_summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.personal_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.routines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.routine_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.weekly_summaries ENABLE ROW LEVEL SECURITY;

-- Profiles: users can read/update their own profile
CREATE POLICY "Users can view own profile" ON public.profiles
    FOR SELECT USING (auth.uid() = id);

CREATE POLICY "Users can update own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = id);

-- Sync devices: users manage their own devices
CREATE POLICY "Users manage own devices" ON public.sync_devices
    FOR ALL USING (auth.uid() = user_id);

-- Workout sessions: users own their data
CREATE POLICY "Users own workout sessions" ON public.workout_sessions
    FOR ALL USING (auth.uid() = user_id);

-- Session summaries: access via session ownership
CREATE POLICY "Users own session summaries" ON public.session_summaries
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.workout_sessions ws
            WHERE ws.id = session_id AND ws.user_id = auth.uid()
        )
    );

-- Personal records: users own their PRs
CREATE POLICY "Users own personal records" ON public.personal_records
    FOR ALL USING (auth.uid() = user_id);

-- Routines: users own their routines
CREATE POLICY "Users own routines" ON public.routines
    FOR ALL USING (auth.uid() = user_id);

-- Routine documents: access via routine ownership
CREATE POLICY "Users own routine documents" ON public.routine_documents
    FOR ALL USING (
        EXISTS (
            SELECT 1 FROM public.routines r
            WHERE r.id = routine_id AND r.user_id = auth.uid()
        )
    );

-- Weekly summaries: users own their summaries
CREATE POLICY "Users own weekly summaries" ON public.weekly_summaries
    FOR ALL USING (auth.uid() = user_id);
```

**Step 3: Commit**

```bash
git add portal/supabase/
git commit -m "feat(portal): add Supabase schema migrations"
```

---

## Task 5: Create Supabase Client for Next.js

**Files:**
- Create: `portal/apps/web/src/lib/supabase/client.ts`
- Create: `portal/apps/web/src/lib/supabase/server.ts`
- Create: `portal/apps/web/src/lib/supabase/middleware.ts`
- Create: `portal/apps/web/.env.local.example`

**Step 1: Create browser client**

Create `portal/apps/web/src/lib/supabase/client.ts`:
```typescript
import { createBrowserClient } from '@supabase/ssr'

export function createClient() {
  return createBrowserClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!
  )
}
```

**Step 2: Create server client**

Create `portal/apps/web/src/lib/supabase/server.ts`:
```typescript
import { createServerClient, type CookieOptions } from '@supabase/ssr'
import { cookies } from 'next/headers'

export async function createClient() {
  const cookieStore = await cookies()

  return createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return cookieStore.getAll()
        },
        setAll(cookiesToSet) {
          try {
            cookiesToSet.forEach(({ name, value, options }) =>
              cookieStore.set(name, value, options)
            )
          } catch {
            // Called from Server Component
          }
        },
      },
    }
  )
}
```

**Step 3: Create middleware helper**

Create `portal/apps/web/src/lib/supabase/middleware.ts`:
```typescript
import { createServerClient } from '@supabase/ssr'
import { NextResponse, type NextRequest } from 'next/server'

export async function updateSession(request: NextRequest) {
  let supabaseResponse = NextResponse.next({
    request,
  })

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll()
        },
        setAll(cookiesToSet) {
          cookiesToSet.forEach(({ name, value, options }) =>
            request.cookies.set(name, value)
          )
          supabaseResponse = NextResponse.next({
            request,
          })
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options)
          )
        },
      },
    }
  )

  await supabase.auth.getUser()

  return supabaseResponse
}
```

**Step 4: Create middleware.ts**

Create `portal/apps/web/src/middleware.ts`:
```typescript
import { type NextRequest } from 'next/server'
import { updateSession } from '@/lib/supabase/middleware'

export async function middleware(request: NextRequest) {
  return await updateSession(request)
}

export const config = {
  matcher: [
    '/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp)$).*)',
  ],
}
```

**Step 5: Create env example**

Create `portal/apps/web/.env.local.example`:
```bash
# Supabase
NEXT_PUBLIC_SUPABASE_URL=your-supabase-url
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-supabase-anon-key

# Ktor Backend
NEXT_PUBLIC_API_URL=http://localhost:8080
```

**Step 6: Commit**

```bash
git add portal/apps/web/src/lib/ portal/apps/web/src/middleware.ts portal/apps/web/.env.local.example
git commit -m "feat(portal): add Supabase client setup"
```

---

## Task 6: Create Auth Pages

**Files:**
- Create: `portal/apps/web/src/app/(auth)/login/page.tsx`
- Create: `portal/apps/web/src/app/(auth)/signup/page.tsx`
- Create: `portal/apps/web/src/app/(auth)/layout.tsx`
- Create: `portal/apps/web/src/app/auth/callback/route.ts`

**Step 1: Create auth layout**

Create `portal/apps/web/src/app/(auth)/layout.tsx`:
```typescript
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center">
          <h1 className="text-3xl font-bold text-phoenix-500">
            Phoenix Portal
          </h1>
          <p className="mt-2 text-gray-600 dark:text-gray-400">
            Premium analytics for your training
          </p>
        </div>
        {children}
      </div>
    </div>
  )
}
```

**Step 2: Create login page**

Create `portal/apps/web/src/app/(auth)/login/page.tsx`:
```typescript
'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { createClient } from '@/lib/supabase/client'

export default function LoginPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)

    const supabase = createClient()
    const { error } = await supabase.auth.signInWithPassword({
      email,
      password,
    })

    if (error) {
      setError(error.message)
      setLoading(false)
    } else {
      router.push('/dashboard')
      router.refresh()
    }
  }

  return (
    <form onSubmit={handleLogin} className="mt-8 space-y-6">
      {error && (
        <div className="bg-red-50 dark:bg-red-900/50 text-red-600 dark:text-red-400 p-3 rounded-md text-sm">
          {error}
        </div>
      )}

      <div className="space-y-4">
        <div>
          <label htmlFor="email" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-phoenix-500 focus:border-phoenix-500"
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
            Password
          </label>
          <input
            id="password"
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-phoenix-500 focus:border-phoenix-500"
          />
        </div>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-phoenix-500 hover:bg-phoenix-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-phoenix-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {loading ? 'Signing in...' : 'Sign in'}
      </button>

      <p className="text-center text-sm text-gray-600 dark:text-gray-400">
        Don't have an account?{' '}
        <Link href="/signup" className="text-phoenix-500 hover:text-phoenix-600">
          Sign up
        </Link>
      </p>
    </form>
  )
}
```

**Step 3: Create signup page**

Create `portal/apps/web/src/app/(auth)/signup/page.tsx`:
```typescript
'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { createClient } from '@/lib/supabase/client'

export default function SignupPage() {
  const router = useRouter()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [success, setSuccess] = useState(false)

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)

    const supabase = createClient()
    const { error } = await supabase.auth.signUp({
      email,
      password,
      options: {
        data: {
          display_name: displayName,
        },
      },
    })

    if (error) {
      setError(error.message)
      setLoading(false)
    } else {
      setSuccess(true)
    }
  }

  if (success) {
    return (
      <div className="mt-8 text-center">
        <div className="bg-green-50 dark:bg-green-900/50 text-green-600 dark:text-green-400 p-4 rounded-md">
          <p className="font-medium">Check your email!</p>
          <p className="mt-1 text-sm">We've sent you a confirmation link.</p>
        </div>
        <Link href="/login" className="mt-4 inline-block text-phoenix-500 hover:text-phoenix-600">
          Back to login
        </Link>
      </div>
    )
  }

  return (
    <form onSubmit={handleSignup} className="mt-8 space-y-6">
      {error && (
        <div className="bg-red-50 dark:bg-red-900/50 text-red-600 dark:text-red-400 p-3 rounded-md text-sm">
          {error}
        </div>
      )}

      <div className="space-y-4">
        <div>
          <label htmlFor="displayName" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
            Display Name
          </label>
          <input
            id="displayName"
            type="text"
            required
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-phoenix-500 focus:border-phoenix-500"
          />
        </div>

        <div>
          <label htmlFor="email" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
            Email
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-phoenix-500 focus:border-phoenix-500"
          />
        </div>

        <div>
          <label htmlFor="password" className="block text-sm font-medium text-gray-700 dark:text-gray-300">
            Password
          </label>
          <input
            id="password"
            type="password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 block w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md shadow-sm bg-white dark:bg-gray-800 text-gray-900 dark:text-white focus:outline-none focus:ring-phoenix-500 focus:border-phoenix-500"
          />
          <p className="mt-1 text-xs text-gray-500">Minimum 8 characters</p>
        </div>
      </div>

      <button
        type="submit"
        disabled={loading}
        className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-phoenix-500 hover:bg-phoenix-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-phoenix-500 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {loading ? 'Creating account...' : 'Sign up'}
      </button>

      <p className="text-center text-sm text-gray-600 dark:text-gray-400">
        Already have an account?{' '}
        <Link href="/login" className="text-phoenix-500 hover:text-phoenix-600">
          Sign in
        </Link>
      </p>
    </form>
  )
}
```

**Step 4: Create auth callback route**

Create `portal/apps/web/src/app/auth/callback/route.ts`:
```typescript
import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get('code')
  const next = searchParams.get('next') ?? '/dashboard'

  if (code) {
    const supabase = await createClient()
    const { error } = await supabase.auth.exchangeCodeForSession(code)
    if (!error) {
      return NextResponse.redirect(`${origin}${next}`)
    }
  }

  return NextResponse.redirect(`${origin}/login?error=auth_error`)
}
```

**Step 5: Commit**

```bash
git add portal/apps/web/src/app/
git commit -m "feat(portal): add auth pages (login, signup, callback)"
```

---

## Task 7: Create Dashboard Placeholder

**Files:**
- Create: `portal/apps/web/src/app/dashboard/page.tsx`
- Create: `portal/apps/web/src/app/dashboard/layout.tsx`

**Step 1: Create dashboard layout with auth check**

Create `portal/apps/web/src/app/dashboard/layout.tsx`:
```typescript
import { redirect } from 'next/navigation'
import { createClient } from '@/lib/supabase/server'

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()

  if (!user) {
    redirect('/login')
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <nav className="bg-white dark:bg-gray-800 shadow">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <span className="text-xl font-bold text-phoenix-500">Phoenix Portal</span>
            </div>
            <div className="flex items-center space-x-4">
              <span className="text-sm text-gray-600 dark:text-gray-400">
                {user.email}
              </span>
              <form action="/auth/signout" method="post">
                <button
                  type="submit"
                  className="text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white"
                >
                  Sign out
                </button>
              </form>
            </div>
          </div>
        </div>
      </nav>
      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        {children}
      </main>
    </div>
  )
}
```

**Step 2: Create dashboard page**

Create `portal/apps/web/src/app/dashboard/page.tsx`:
```typescript
import { createClient } from '@/lib/supabase/server'

export default async function DashboardPage() {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()

  // Fetch profile
  const { data: profile } = await supabase
    .from('profiles')
    .select('*')
    .eq('id', user?.id)
    .single()

  return (
    <div className="space-y-6">
      <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <h2 className="text-2xl font-bold text-gray-900 dark:text-white">
          Welcome, {profile?.display_name || 'Athlete'}!
        </h2>
        <p className="mt-2 text-gray-600 dark:text-gray-400">
          Your premium analytics dashboard is coming soon.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
          <h3 className="text-lg font-medium text-gray-900 dark:text-white">Workouts</h3>
          <p className="mt-2 text-3xl font-bold text-phoenix-500">--</p>
          <p className="text-sm text-gray-500">Total sessions</p>
        </div>

        <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
          <h3 className="text-lg font-medium text-gray-900 dark:text-white">Volume</h3>
          <p className="mt-2 text-3xl font-bold text-phoenix-500">-- kg</p>
          <p className="text-sm text-gray-500">This week</p>
        </div>

        <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
          <h3 className="text-lg font-medium text-gray-900 dark:text-white">PRs</h3>
          <p className="mt-2 text-3xl font-bold text-phoenix-500">--</p>
          <p className="text-sm text-gray-500">Personal records</p>
        </div>
      </div>

      <div className="bg-white dark:bg-gray-800 shadow rounded-lg p-6">
        <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-4">
          Sync Status
        </h3>
        <p className="text-gray-600 dark:text-gray-400">
          No data synced yet. Connect your Phoenix mobile app to see your analytics here.
        </p>
      </div>
    </div>
  )
}
```

**Step 3: Create signout route**

Create `portal/apps/web/src/app/auth/signout/route.ts`:
```typescript
import { createClient } from '@/lib/supabase/server'
import { NextResponse } from 'next/server'

export async function POST(request: Request) {
  const supabase = await createClient()
  await supabase.auth.signOut()

  const { origin } = new URL(request.url)
  return NextResponse.redirect(`${origin}/login`)
}
```

**Step 4: Commit**

```bash
git add portal/apps/web/src/app/dashboard/ portal/apps/web/src/app/auth/signout/
git commit -m "feat(portal): add dashboard placeholder with auth protection"
```

---

## Task 8: Create Basic Sync Endpoint in Ktor

**Files:**
- Create: `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/models/SyncModels.kt`
- Create: `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/routes/SyncRoutes.kt`
- Modify: `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/Application.kt`

**Step 1: Create sync models**

Create `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/models/SyncModels.kt`:
```kotlin
package com.devil.phoenixproject.portal.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SyncPushRequest(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null,
    val changes: List<SyncChange>
)

@Serializable
data class SyncChange(
    val table: String,
    val operation: String, // "upsert" or "delete"
    val rowId: String,
    val baseVersion: Int? = null,
    val payload: JsonElement
)

@Serializable
data class SyncPushResponse(
    val accepted: List<String>,
    val conflicts: List<SyncConflict>,
    val newCursor: String,
    val serverTime: Long
)

@Serializable
data class SyncConflict(
    val rowId: String,
    val table: String,
    val clientVersion: Int,
    val serverVersion: Int,
    val serverData: JsonElement
)

@Serializable
data class SyncPullRequest(
    val deviceId: String,
    val cursor: String? = null,
    val tables: List<String>? = null
)

@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val newCursor: String,
    val hasMore: Boolean
)
```

**Step 2: Create sync routes**

Create `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/routes/SyncRoutes.kt`:
```kotlin
package com.devil.phoenixproject.portal.routes

import com.devil.phoenixproject.portal.models.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun Route.syncRoutes() {
    route("/api/sync") {

        post("/push") {
            // TODO: Validate JWT token from Authorization header
            // TODO: Extract user_id from token

            val request = call.receive<SyncPushRequest>()

            // For now, just acknowledge all changes
            // Real implementation will:
            // 1. Validate auth
            // 2. Check versions for conflicts
            // 3. Apply changes to PostgreSQL
            // 4. Return conflicts if any

            val accepted = request.changes.map { it.rowId }
            val serverTime = System.currentTimeMillis()

            call.respond(
                SyncPushResponse(
                    accepted = accepted,
                    conflicts = emptyList(),
                    newCursor = serverTime.toString(),
                    serverTime = serverTime
                )
            )
        }

        post("/pull") {
            // TODO: Validate JWT token
            // TODO: Fetch changes since cursor

            val request = call.receive<SyncPullRequest>()

            // For now, return empty changes
            call.respond(
                SyncPullResponse(
                    changes = emptyList(),
                    newCursor = System.currentTimeMillis().toString(),
                    hasMore = false
                )
            )
        }

        get("/status") {
            // Health check for sync service
            call.respond(mapOf(
                "status" to "operational",
                "version" to "0.1.0"
            ))
        }
    }
}
```

**Step 3: Update Application.kt to include sync routes**

Modify `portal/apps/backend/src/main/kotlin/com/devil/phoenixproject/portal/Application.kt`:
```kotlin
package com.devil.phoenixproject.portal

import com.devil.phoenixproject.portal.routes.syncRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.INFO
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost() // Configure properly for production
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = "500: ${cause.localizedMessage}",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    routing {
        get("/") {
            call.respondText("Phoenix Portal API v0.1.0")
        }

        get("/health") {
            call.respond(mapOf("status" to "healthy"))
        }

        // Sync endpoints
        syncRoutes()
    }
}
```

**Step 4: Verify build and test endpoint**

Run from `portal/apps/backend/`:
```bash
./gradlew build
./gradlew run
```

Test the endpoint:
```bash
curl -X POST http://localhost:8080/api/sync/push \
  -H "Content-Type: application/json" \
  -d '{"deviceId": "test-device", "changes": []}'
```

Expected: `{"accepted":[],"conflicts":[],"newCursor":"...","serverTime":...}`

**Step 5: Commit**

```bash
git add portal/apps/backend/src/
git commit -m "feat(portal): add basic sync endpoints"
```

---

## Task 9: Final Integration Test

**Step 1: Start all services**

Terminal 1 - Backend:
```bash
cd portal/apps/backend && ./gradlew run
```

Terminal 2 - Frontend:
```bash
cd portal && npm run dev:web
```

**Step 2: Verify endpoints**

- Backend health: `curl http://localhost:8080/health` → `{"status":"healthy"}`
- Sync status: `curl http://localhost:8080/api/sync/status` → `{"status":"operational",...}`
- Frontend: Open `http://localhost:3000` → See Phoenix Portal home page
- Login page: Open `http://localhost:3000/login` → See login form

**Step 3: Create final commit**

```bash
git add -A
git commit -m "feat(portal): complete Phase 1 foundation

- Monorepo structure with npm workspaces
- Next.js 14 frontend with Tailwind CSS
- Ktor backend with sync endpoints
- Supabase schema migrations
- Auth flow (login, signup, callback)
- Protected dashboard page
- Basic sync push/pull endpoints"
```

---

## Phase 1 Complete Checklist

- [ ] Monorepo structure created
- [ ] Next.js frontend running
- [ ] Ktor backend running
- [ ] Supabase migrations ready
- [ ] Auth pages (login, signup) working
- [ ] Dashboard with auth protection
- [ ] Basic sync endpoints responding
- [ ] All code committed

---

## Next Steps (Phase 2 Preview)

After completing Phase 1, you'll need to:

1. **Apply Supabase migrations** to your project
2. **Configure environment variables** in `.env.local`
3. **Deploy to Azure** (Container Apps for Ktor, Static Web Apps for Next.js)
4. Begin **Phase 2: Analytics MVP** (charts, body model, history views)

---

*Plan generated from design document: `docs/plans/2026-01-03-phoenix-web-portal-design.md`*
