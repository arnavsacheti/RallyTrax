package com.rallytrax.app.data.local.entity

/**
 * How a vehicle relates to the user. Drives on any vehicle still attribute to
 * the user (Profile / Year-in-Review totals), but only OWNED vehicles surface
 * in the Garage default view, accumulate maintenance schedules, and feed the
 * long-term MPG aggregation. BORROWED and RENTED skip that machinery so a
 * one-off trip doesn't litter the Garage with rentals or trip the
 * "maintenance due" warnings on a friend's car.
 */
enum class Ownership {
    OWNED,
    BORROWED,
    RENTED;

    companion object {
        fun fromStorage(raw: String?): Ownership = when (raw) {
            BORROWED.name -> BORROWED
            RENTED.name -> RENTED
            else -> OWNED
        }
    }
}
