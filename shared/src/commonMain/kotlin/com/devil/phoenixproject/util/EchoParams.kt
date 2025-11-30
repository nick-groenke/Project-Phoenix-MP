package com.devil.phoenixproject.util

/**
 * Echo parameters data class
 *
 * Contains all the parameters needed for Echo mode control frame construction.
 * These values are used to configure velocity-based isokinetic training.
 */
data class EchoParams(
    val eccentricPct: Int,
    val concentricPct: Int,
    val smoothing: Float,
    val floor: Float,
    val negLimit: Float,
    val gain: Float,
    val cap: Float
)
