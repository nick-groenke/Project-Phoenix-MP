package com.devil.phoenixproject.portal.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 255).nullable()
    val isPremium = bool("is_premium").default(false)
    val createdAt = timestamp("created_at")
    val lastLoginAt = timestamp("last_login_at").nullable()
    val lastSyncAt = timestamp("last_sync_at").nullable()
    val subscriptionStatus = varchar("subscription_status", 50).default("free")
    val subscriptionExpiresAt = timestamp("subscription_expires_at").nullable()
}

object WorkoutSessions : UUIDTable("workout_sessions") {
    val userId = reference("user_id", Users)

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // Core workout data
    val timestamp = long("timestamp")
    val mode = varchar("mode", 50)
    val targetReps = integer("target_reps")
    val weightPerCableKg = float("weight_per_cable_kg")
    val progressionKg = float("progression_kg").default(0f)
    val duration = integer("duration").default(0)
    val totalReps = integer("total_reps").default(0)
    val warmupReps = integer("warmup_reps").default(0)
    val workingReps = integer("working_reps").default(0)
    val isJustLift = bool("is_just_lift").default(false)
    val stopAtTop = bool("stop_at_top").default(false)
    val eccentricLoad = integer("eccentric_load").default(100)
    val echoLevel = integer("echo_level").default(1)

    // Exercise reference
    val exerciseId = varchar("exercise_id", 255).nullable()
    val exerciseName = varchar("exercise_name", 255).nullable()
    val routineSessionId = varchar("routine_session_id", 255).nullable()
    val routineName = varchar("routine_name", 255).nullable()

    // Safety tracking
    val safetyFlags = integer("safety_flags").default(0)
    val deloadWarningCount = integer("deload_warning_count").default(0)
    val romViolationCount = integer("rom_violation_count").default(0)
    val spotterActivations = integer("spotter_activations").default(0)

    // Force metrics
    val peakForceConcentricA = float("peak_force_concentric_a").nullable()
    val peakForceConcentricB = float("peak_force_concentric_b").nullable()
    val peakForceEccentricA = float("peak_force_eccentric_a").nullable()
    val peakForceEccentricB = float("peak_force_eccentric_b").nullable()
    val avgForceConcentricA = float("avg_force_concentric_a").nullable()
    val avgForceConcentricB = float("avg_force_concentric_b").nullable()
    val avgForceEccentricA = float("avg_force_eccentric_a").nullable()
    val avgForceEccentricB = float("avg_force_eccentric_b").nullable()

    // Summary metrics
    val heaviestLiftKg = float("heaviest_lift_kg").nullable()
    val totalVolumeKg = float("total_volume_kg").nullable()
    val estimatedCalories = float("estimated_calories").nullable()
    val warmupAvgWeightKg = float("warmup_avg_weight_kg").nullable()
    val workingAvgWeightKg = float("working_avg_weight_kg").nullable()
    val burnoutAvgWeightKg = float("burnout_avg_weight_kg").nullable()
    val peakWeightKg = float("peak_weight_kg").nullable()
    val rpe = integer("rpe").nullable()

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PersonalRecords : UUIDTable("personal_records") {
    val userId = reference("user_id", Users)

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // PR data
    val exerciseId = varchar("exercise_id", 255)
    val exerciseName = varchar("exercise_name", 255)
    val weight = float("weight")
    val reps = integer("reps")
    val oneRepMax = float("one_rep_max")
    val achievedAt = long("achieved_at")
    val workoutMode = varchar("workout_mode", 50)
    val prType = varchar("pr_type", 50).default("MAX_WEIGHT")
    val volume = float("volume").default(0f)
    val sessionId = uuid("session_id").nullable()

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object PhaseStatistics : UUIDTable("phase_statistics") {
    val userId = reference("user_id", Users)
    val sessionId = reference("session_id", WorkoutSessions)

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // Concentric phase
    val concentricKgAvg = float("concentric_kg_avg")
    val concentricKgMax = float("concentric_kg_max")
    val concentricVelAvg = float("concentric_vel_avg")
    val concentricVelMax = float("concentric_vel_max")
    val concentricWattAvg = float("concentric_watt_avg")
    val concentricWattMax = float("concentric_watt_max")

    // Eccentric phase
    val eccentricKgAvg = float("eccentric_kg_avg")
    val eccentricKgMax = float("eccentric_kg_max")
    val eccentricVelAvg = float("eccentric_vel_avg")
    val eccentricVelMax = float("eccentric_vel_max")
    val eccentricWattAvg = float("eccentric_watt_avg")
    val eccentricWattMax = float("eccentric_watt_max")

    // Timestamps
    val timestamp = long("timestamp")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Routines : UUIDTable("routines") {
    val userId = reference("user_id", Users)

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // Routine data
    val name = varchar("name", 255)
    val description = text("description").default("")
    val lastUsed = long("last_used").nullable()
    val useCount = integer("use_count").default(0)

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object Supersets : UUIDTable("supersets") {
    val userId = reference("user_id", Users)
    val routineId = reference("routine_id", Routines)

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // Superset data
    val name = varchar("name", 255)
    val colorIndex = integer("color_index").default(0)
    val restBetweenSeconds = integer("rest_between_seconds").default(10)
    val orderIndex = integer("order_index")

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object RoutineExercises : UUIDTable("routine_exercises") {
    val userId = reference("user_id", Users)
    val routineId = reference("routine_id", Routines)
    val supersetId = reference("superset_id", Supersets).nullable()

    // Sync metadata
    val clientId = uuid("client_id").uniqueIndex()
    val deviceId = uuid("device_id")
    val deletedAt = timestamp("deleted_at").nullable()

    // Exercise reference
    val exerciseId = varchar("exercise_id", 255).nullable()
    val exerciseName = varchar("exercise_name", 255)
    val exerciseMuscleGroup = varchar("exercise_muscle_group", 100).default("")
    val exerciseEquipment = varchar("exercise_equipment", 100).default("")
    val exerciseDefaultCableConfig = varchar("exercise_default_cable_config", 50).default("DOUBLE")

    // Configuration
    val cableConfig = varchar("cable_config", 50).default("DOUBLE")
    val orderIndex = integer("order_index")
    val setReps = varchar("set_reps", 255).default("10,10,10")
    val weightPerCableKg = float("weight_per_cable_kg").default(0f)
    val setWeights = varchar("set_weights", 500).default("")
    val mode = varchar("mode", 50).default("OldSchool")
    val eccentricLoad = integer("eccentric_load").default(100)
    val echoLevel = integer("echo_level").default(1)
    val progressionKg = float("progression_kg").default(0f)
    val restSeconds = integer("rest_seconds").default(60)
    val duration = integer("duration").nullable()
    val setRestSeconds = varchar("set_rest_seconds", 255).default("[]")
    val perSetRestTime = integer("per_set_rest_time").default(0)
    val isAMRAP = bool("is_amrap").default(false)
    val orderInSuperset = integer("order_in_superset").default(0)

    // Timestamps
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
