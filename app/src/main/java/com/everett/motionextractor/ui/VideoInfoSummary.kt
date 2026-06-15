package com.everett.motionextractor.ui

data class VideoInfoSummary(
    val width: Int,
    val height: Int,
    val fps: Double,
    val durationMs: Long,
    val rotationDegrees: Int
)
