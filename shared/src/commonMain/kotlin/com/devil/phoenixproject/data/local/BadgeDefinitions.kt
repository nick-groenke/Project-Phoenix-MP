package com.devil.phoenixproject.data.local

import com.devil.phoenixproject.domain.model.*
import com.devil.phoenixproject.domain.model.BadgeCategory.*
import com.devil.phoenixproject.domain.model.BadgeTier.*
import com.devil.phoenixproject.domain.model.BadgeRequirement.*

/**
 * All badge definitions for the gamification system.
 * Badges are grouped by category and tier for easy reference.
 */
object BadgeDefinitions {

    /**
     * All available badges in the system
     */
    val allBadges: List<Badge> = listOf(
        // ==================== CONSISTENCY BADGES (Streak-based) ====================
        Badge(
            id = "streak_3",
            name = "Getting Started",
            description = "Maintain a 3-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = BRONZE,
            requirement = StreakDays(3)
        ),
        Badge(
            id = "streak_7",
            name = "Week Warrior",
            description = "Maintain a 7-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = SILVER,
            requirement = StreakDays(7)
        ),
        Badge(
            id = "streak_14",
            name = "Fortnight Fighter",
            description = "Maintain a 14-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = SILVER,
            requirement = StreakDays(14)
        ),
        Badge(
            id = "streak_30",
            name = "Monthly Dedication",
            description = "Maintain a 30-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = GOLD,
            requirement = StreakDays(30)
        ),
        Badge(
            id = "streak_60",
            name = "Iron Discipline",
            description = "Maintain a 60-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = GOLD,
            requirement = StreakDays(60)
        ),
        Badge(
            id = "streak_100",
            name = "Centurion",
            description = "Maintain a 100-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = PLATINUM,
            requirement = StreakDays(100)
        ),
        Badge(
            id = "streak_365",
            name = "Year of Iron",
            description = "Maintain a 365-day workout streak",
            category = CONSISTENCY,
            iconResource = "fire",
            tier = PLATINUM,
            requirement = StreakDays(365),
            isSecret = true
        ),

        // ==================== DEDICATION BADGES (Workout count) ====================
        Badge(
            id = "workouts_1",
            name = "First Rep",
            description = "Complete your first workout",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = BRONZE,
            requirement = TotalWorkouts(1)
        ),
        Badge(
            id = "workouts_10",
            name = "Getting Hooked",
            description = "Complete 10 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = BRONZE,
            requirement = TotalWorkouts(10)
        ),
        Badge(
            id = "workouts_25",
            name = "Building Habit",
            description = "Complete 25 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = SILVER,
            requirement = TotalWorkouts(25)
        ),
        Badge(
            id = "workouts_50",
            name = "Regular",
            description = "Complete 50 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = SILVER,
            requirement = TotalWorkouts(50)
        ),
        Badge(
            id = "workouts_100",
            name = "Committed",
            description = "Complete 100 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = GOLD,
            requirement = TotalWorkouts(100)
        ),
        Badge(
            id = "workouts_250",
            name = "Dedicated",
            description = "Complete 250 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = GOLD,
            requirement = TotalWorkouts(250)
        ),
        Badge(
            id = "workouts_500",
            name = "Iron Will",
            description = "Complete 500 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = PLATINUM,
            requirement = TotalWorkouts(500)
        ),
        Badge(
            id = "workouts_1000",
            name = "Legend",
            description = "Complete 1,000 workouts",
            category = DEDICATION,
            iconResource = "dumbbell",
            tier = PLATINUM,
            requirement = TotalWorkouts(1000),
            isSecret = true
        ),

        // ==================== STRENGTH BADGES (PR-based) ====================
        Badge(
            id = "pr_1",
            name = "Personal Best",
            description = "Achieve your first personal record",
            category = STRENGTH,
            iconResource = "trophy",
            tier = BRONZE,
            requirement = PRsAchieved(1)
        ),
        Badge(
            id = "pr_5",
            name = "Record Setter",
            description = "Achieve 5 personal records",
            category = STRENGTH,
            iconResource = "trophy",
            tier = BRONZE,
            requirement = PRsAchieved(5)
        ),
        Badge(
            id = "pr_10",
            name = "Record Breaker",
            description = "Achieve 10 personal records",
            category = STRENGTH,
            iconResource = "trophy",
            tier = SILVER,
            requirement = PRsAchieved(10)
        ),
        Badge(
            id = "pr_25",
            name = "Strength Climber",
            description = "Achieve 25 personal records",
            category = STRENGTH,
            iconResource = "trophy",
            tier = SILVER,
            requirement = PRsAchieved(25)
        ),
        Badge(
            id = "pr_50",
            name = "PR Machine",
            description = "Achieve 50 personal records",
            category = STRENGTH,
            iconResource = "trophy",
            tier = GOLD,
            requirement = PRsAchieved(50)
        ),
        Badge(
            id = "pr_100",
            name = "Record Legend",
            description = "Achieve 100 personal records",
            category = STRENGTH,
            iconResource = "trophy",
            tier = PLATINUM,
            requirement = PRsAchieved(100)
        ),

        // ==================== VOLUME BADGES (Rep count) ====================
        Badge(
            id = "reps_100",
            name = "First Century",
            description = "Complete 100 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = BRONZE,
            requirement = TotalReps(100)
        ),
        Badge(
            id = "reps_500",
            name = "Rep Rookie",
            description = "Complete 500 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = BRONZE,
            requirement = TotalReps(500)
        ),
        Badge(
            id = "reps_1000",
            name = "Thousand Club",
            description = "Complete 1,000 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = SILVER,
            requirement = TotalReps(1000)
        ),
        Badge(
            id = "reps_5000",
            name = "Rep Warrior",
            description = "Complete 5,000 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = SILVER,
            requirement = TotalReps(5000)
        ),
        Badge(
            id = "reps_10000",
            name = "Rep Master",
            description = "Complete 10,000 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = GOLD,
            requirement = TotalReps(10000)
        ),
        Badge(
            id = "reps_50000",
            name = "Rep Champion",
            description = "Complete 50,000 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = GOLD,
            requirement = TotalReps(50000)
        ),
        Badge(
            id = "reps_100000",
            name = "Rep Legend",
            description = "Complete 100,000 reps total",
            category = VOLUME,
            iconResource = "repeat",
            tier = PLATINUM,
            requirement = TotalReps(100000)
        ),

        // ==================== EXPLORER BADGES (Exercise variety) ====================
        Badge(
            id = "exercises_5",
            name = "Curious",
            description = "Try 5 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = BRONZE,
            requirement = UniqueExercises(5)
        ),
        Badge(
            id = "exercises_10",
            name = "Experimenter",
            description = "Try 10 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = BRONZE,
            requirement = UniqueExercises(10)
        ),
        Badge(
            id = "exercises_20",
            name = "Adventurer",
            description = "Try 20 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = SILVER,
            requirement = UniqueExercises(20)
        ),
        Badge(
            id = "exercises_35",
            name = "Versatile",
            description = "Try 35 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = SILVER,
            requirement = UniqueExercises(35)
        ),
        Badge(
            id = "exercises_50",
            name = "Explorer",
            description = "Try 50 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = GOLD,
            requirement = UniqueExercises(50)
        ),
        Badge(
            id = "exercises_75",
            name = "Master Explorer",
            description = "Try 75 different exercises",
            category = EXPLORER,
            iconResource = "compass",
            tier = PLATINUM,
            requirement = UniqueExercises(75)
        ),

        // ==================== SECRET BADGES (Hidden until earned) ====================
        Badge(
            id = "early_bird",
            name = "Early Bird",
            description = "Complete a workout before 6 AM",
            category = DEDICATION,
            iconResource = "sun",
            tier = GOLD,
            requirement = WorkoutAtTime(0, 6),
            isSecret = true
        ),
        Badge(
            id = "night_owl",
            name = "Night Owl",
            description = "Complete a workout after 10 PM",
            category = DEDICATION,
            iconResource = "moon",
            tier = GOLD,
            requirement = WorkoutAtTime(22, 24),
            isSecret = true
        ),
        Badge(
            id = "weekend_warrior",
            name = "Weekend Warrior",
            description = "Complete 5 workouts in a single week",
            category = CONSISTENCY,
            iconResource = "calendar",
            tier = SILVER,
            requirement = WorkoutsInWeek(5),
            isSecret = true
        ),
        Badge(
            id = "marathon_session",
            name = "Marathon Session",
            description = "Lift over 5,000 kg in a single workout",
            category = VOLUME,
            iconResource = "weight",
            tier = GOLD,
            requirement = SingleWorkoutVolume(5000),
            isSecret = true
        )
    )

    /**
     * Get badge by ID
     */
    fun getBadgeById(id: String): Badge? = allBadges.find { it.id == id }

    /**
     * Get badges by category
     */
    fun getBadgesByCategory(category: BadgeCategory): List<Badge> =
        allBadges.filter { it.category == category }

    /**
     * Get badges by tier
     */
    fun getBadgesByTier(tier: BadgeTier): List<Badge> =
        allBadges.filter { it.tier == tier }

    /**
     * Get non-secret badges (visible when locked)
     */
    fun getVisibleBadges(): List<Badge> = allBadges.filter { !it.isSecret }

    /**
     * Get secret badges (only shown when earned)
     */
    fun getSecretBadges(): List<Badge> = allBadges.filter { it.isSecret }

    /**
     * Total badge count
     */
    val totalBadgeCount: Int = allBadges.size
}
