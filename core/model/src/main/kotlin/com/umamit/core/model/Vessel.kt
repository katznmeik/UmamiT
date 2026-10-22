package com.umamit.core.model

/** A vessel (teaware, 05 §3.4). */
data class Vessel(
    val id: String,
    val name: String,
    val type: VesselType,
    val capacityMl: Int,
    val usableMl: Int? = null,
    val material: VesselMaterial? = null,
    val notes: String? = null,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val coverPhotoId: String? = null,
)
