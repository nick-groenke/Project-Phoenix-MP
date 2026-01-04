-- Phoenix Portal Initial Schema
-- This migration creates all core tables for the Phoenix web portal
-- Synced from mobile app + cloud-only social features

-- ============================================================================
-- CORE TABLES (Synced from Mobile)
-- ============================================================================

-- User profiles (extends Supabase auth.users)
CREATE TABLE profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT,
    avatar_url TEXT,
    is_premium BOOLEAN DEFAULT FALSE,
    subscription_status TEXT DEFAULT 'free',
    subscription_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_synced_at TIMESTAMPTZ
);

-- Trigger to create profile on user signup
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO profiles (id, display_name, created_at)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'display_name', NEW.email),
        NOW()
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- Sync devices for multi-device tracking
CREATE TABLE sync_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    device_id TEXT NOT NULL,
    device_name TEXT,
    platform TEXT,
    last_sync_at TIMESTAMPTZ DEFAULT NOW(),
    sync_cursor BIGINT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, device_id)
);

-- Workout sessions with sync metadata
CREATE TABLE workout_sessions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    -- Core fields from mobile SQLite
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

-- Pre-computed session analytics
CREATE TABLE session_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID REFERENCES workout_sessions(id) ON DELETE CASCADE,
    rep_velocities JSONB,           -- [{rep: 1, peak: 0.5, avg: 0.3}, ...]
    rep_powers JSONB,
    rep_tut_ms JSONB,
    sticking_point_position_mm REAL,
    sticking_point_min_velocity REAL,
    left_right_imbalance_percent REAL,
    position_force_bins JSONB,      -- 20x20 grid for heatmap
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Personal records
CREATE TABLE personal_records (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
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

-- Routines
CREATE TABLE routines (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
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

-- Routine exercises as JSON document
CREATE TABLE routine_documents (
    routine_id UUID PRIMARY KEY REFERENCES routines(id) ON DELETE CASCADE,
    exercises_json JSONB NOT NULL,
    supersets_json JSONB,
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    version INTEGER DEFAULT 1
);

-- Weekly rollups for trend analysis
CREATE TABLE weekly_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    total_volume_kg REAL DEFAULT 0,
    total_reps INTEGER DEFAULT 0,
    workout_count INTEGER DEFAULT 0,
    avg_intensity REAL,
    muscle_group_distribution JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, week_start)
);

-- ============================================================================
-- SOCIAL TABLES (Cloud-Only)
-- ============================================================================

-- Shared routine snapshots (immutable)
CREATE TABLE shared_routine_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID REFERENCES profiles(id) ON DELETE SET NULL,
    author_display_name TEXT,
    source_routine_id UUID,
    name TEXT NOT NULL,
    description TEXT,
    routine_doc JSONB NOT NULL,
    tags TEXT[],
    difficulty_level TEXT,
    estimated_duration_min INTEGER,
    flame_count INTEGER DEFAULT 0,
    download_count INTEGER DEFAULT 0,
    published_at TIMESTAMPTZ DEFAULT NOW(),
    is_featured BOOLEAN DEFAULT FALSE
);

-- Flames (upvotes)
CREATE TABLE flames (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    snapshot_id UUID REFERENCES shared_routine_snapshots(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, snapshot_id)
);

-- Routine downloads
CREATE TABLE routine_downloads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    snapshot_id UUID REFERENCES shared_routine_snapshots(id) ON DELETE CASCADE,
    downloaded_at TIMESTAMPTZ DEFAULT NOW(),
    imported_as_routine_id UUID,
    UNIQUE(user_id, snapshot_id)
);

-- Challenges
CREATE TABLE challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    description TEXT,
    goal_type TEXT NOT NULL,  -- TOTAL_VOLUME, WORKOUT_COUNT, STREAK, SPECIFIC_EXERCISE
    goal_target REAL NOT NULL,
    goal_exercise_id TEXT,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    created_by UUID REFERENCES profiles(id),
    is_official BOOLEAN DEFAULT FALSE,
    participant_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Challenge participants
CREATE TABLE challenge_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id UUID REFERENCES challenges(id) ON DELETE CASCADE,
    user_id UUID REFERENCES profiles(id) ON DELETE CASCADE,
    current_progress REAL DEFAULT 0,
    last_updated TIMESTAMPTZ DEFAULT NOW(),
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(challenge_id, user_id)
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- User data lookups
CREATE INDEX idx_workout_sessions_user_id ON workout_sessions(user_id);
CREATE INDEX idx_workout_sessions_timestamp ON workout_sessions(timestamp DESC);
CREATE INDEX idx_workout_sessions_exercise_id ON workout_sessions(exercise_id);
CREATE INDEX idx_workout_sessions_user_timestamp ON workout_sessions(user_id, timestamp DESC);
CREATE INDEX idx_workout_sessions_sync ON workout_sessions(user_id, updated_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_personal_records_user_id ON personal_records(user_id);
CREATE INDEX idx_personal_records_exercise ON personal_records(user_id, exercise_id);

CREATE INDEX idx_routines_user_id ON routines(user_id);
CREATE INDEX idx_routines_sync ON routines(user_id, updated_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_weekly_summaries_user_week ON weekly_summaries(user_id, week_start DESC);

CREATE INDEX idx_session_summaries_session_id ON session_summaries(session_id);

CREATE INDEX idx_sync_devices_user_id ON sync_devices(user_id);

-- Social feature lookups
CREATE INDEX idx_shared_routines_author ON shared_routine_snapshots(author_id);
CREATE INDEX idx_shared_routines_flames ON shared_routine_snapshots(flame_count DESC);
CREATE INDEX idx_shared_routines_downloads ON shared_routine_snapshots(download_count DESC);
CREATE INDEX idx_shared_routines_published ON shared_routine_snapshots(published_at DESC);
CREATE INDEX idx_shared_routines_featured ON shared_routine_snapshots(is_featured) WHERE is_featured = TRUE;

CREATE INDEX idx_flames_user_id ON flames(user_id);
CREATE INDEX idx_flames_snapshot_id ON flames(snapshot_id);

CREATE INDEX idx_downloads_user_id ON routine_downloads(user_id);
CREATE INDEX idx_downloads_snapshot_id ON routine_downloads(snapshot_id);

CREATE INDEX idx_challenges_dates ON challenges(start_date, end_date);
CREATE INDEX idx_challenges_official ON challenges(is_official) WHERE is_official = TRUE;

CREATE INDEX idx_participants_challenge ON challenge_participants(challenge_id);
CREATE INDEX idx_participants_user ON challenge_participants(user_id);

-- ============================================================================
-- ROW LEVEL SECURITY
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE workout_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE session_summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE personal_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE routines ENABLE ROW LEVEL SECURITY;
ALTER TABLE routine_documents ENABLE ROW LEVEL SECURITY;
ALTER TABLE weekly_summaries ENABLE ROW LEVEL SECURITY;
ALTER TABLE shared_routine_snapshots ENABLE ROW LEVEL SECURITY;
ALTER TABLE flames ENABLE ROW LEVEL SECURITY;
ALTER TABLE routine_downloads ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE challenge_participants ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- RLS POLICIES
-- ============================================================================

-- Profiles: Users can read any profile, but only update their own
CREATE POLICY "Public profiles are viewable by everyone"
    ON profiles FOR SELECT
    USING (true);

CREATE POLICY "Users can update own profile"
    ON profiles FOR UPDATE
    USING (auth.uid() = id);

-- Sync devices: Users can only manage their own devices
CREATE POLICY "Users own their sync devices"
    ON sync_devices FOR ALL
    USING (auth.uid() = user_id);

-- Workout sessions: Users can only access their own data
CREATE POLICY "Users own their workout sessions"
    ON workout_sessions FOR ALL
    USING (auth.uid() = user_id);

-- Session summaries: Through workout_sessions ownership
CREATE POLICY "Users own their session summaries"
    ON session_summaries FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM workout_sessions ws
            WHERE ws.id = session_summaries.session_id
            AND ws.user_id = auth.uid()
        )
    );

-- Personal records: Users can only access their own
CREATE POLICY "Users own their personal records"
    ON personal_records FOR ALL
    USING (auth.uid() = user_id);

-- Routines: Users can only access their own
CREATE POLICY "Users own their routines"
    ON routines FOR ALL
    USING (auth.uid() = user_id);

-- Routine documents: Through routine ownership
CREATE POLICY "Users own their routine documents"
    ON routine_documents FOR ALL
    USING (
        EXISTS (
            SELECT 1 FROM routines r
            WHERE r.id = routine_documents.routine_id
            AND r.user_id = auth.uid()
        )
    );

-- Weekly summaries: Users can only access their own
CREATE POLICY "Users own their weekly summaries"
    ON weekly_summaries FOR ALL
    USING (auth.uid() = user_id);

-- Shared routine snapshots: Anyone can read, only author can manage
CREATE POLICY "Public shared routines are viewable by everyone"
    ON shared_routine_snapshots FOR SELECT
    USING (true);

CREATE POLICY "Authors can insert shared routines"
    ON shared_routine_snapshots FOR INSERT
    WITH CHECK (auth.uid() = author_id);

CREATE POLICY "Authors can update own shared routines"
    ON shared_routine_snapshots FOR UPDATE
    USING (auth.uid() = author_id);

CREATE POLICY "Authors can delete own shared routines"
    ON shared_routine_snapshots FOR DELETE
    USING (auth.uid() = author_id);

-- Flames: Users can manage their own flames
CREATE POLICY "Users can view all flames"
    ON flames FOR SELECT
    USING (true);

CREATE POLICY "Users can manage their own flames"
    ON flames FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete their own flames"
    ON flames FOR DELETE
    USING (auth.uid() = user_id);

-- Routine downloads: Users can manage their own downloads
CREATE POLICY "Users can manage their own downloads"
    ON routine_downloads FOR ALL
    USING (auth.uid() = user_id);

-- Challenges: Anyone can read, only creators can manage
CREATE POLICY "Public challenges are viewable by everyone"
    ON challenges FOR SELECT
    USING (true);

CREATE POLICY "Creators can manage their challenges"
    ON challenges FOR ALL
    USING (auth.uid() = created_by);

-- Challenge participants: Users can manage their own participation
CREATE POLICY "Anyone can view challenge participants"
    ON challenge_participants FOR SELECT
    USING (true);

CREATE POLICY "Users can manage their own participation"
    ON challenge_participants FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own progress"
    ON challenge_participants FOR UPDATE
    USING (auth.uid() = user_id);

CREATE POLICY "Users can leave challenges"
    ON challenge_participants FOR DELETE
    USING (auth.uid() = user_id);

-- ============================================================================
-- TRIGGERS FOR FLAME/DOWNLOAD COUNTS
-- ============================================================================

-- Update flame count on insert
CREATE OR REPLACE FUNCTION update_flame_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE shared_routine_snapshots
        SET flame_count = flame_count + 1
        WHERE id = NEW.snapshot_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE shared_routine_snapshots
        SET flame_count = flame_count - 1
        WHERE id = OLD.snapshot_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_flame_change
    AFTER INSERT OR DELETE ON flames
    FOR EACH ROW EXECUTE FUNCTION update_flame_count();

-- Update download count on insert
CREATE OR REPLACE FUNCTION update_download_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE shared_routine_snapshots
    SET download_count = download_count + 1
    WHERE id = NEW.snapshot_id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_download
    AFTER INSERT ON routine_downloads
    FOR EACH ROW EXECUTE FUNCTION update_download_count();

-- Update participant count on join/leave
CREATE OR REPLACE FUNCTION update_participant_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE challenges
        SET participant_count = participant_count + 1
        WHERE id = NEW.challenge_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE challenges
        SET participant_count = participant_count - 1
        WHERE id = OLD.challenge_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_participant_change
    AFTER INSERT OR DELETE ON challenge_participants
    FOR EACH ROW EXECUTE FUNCTION update_participant_count();

-- ============================================================================
-- UPDATED_AT TRIGGER
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER workout_sessions_updated_at
    BEFORE UPDATE ON workout_sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER routines_updated_at
    BEFORE UPDATE ON routines
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER routine_documents_updated_at
    BEFORE UPDATE ON routine_documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
