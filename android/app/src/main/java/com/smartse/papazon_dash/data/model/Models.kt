package com.smartse.papazon_dash.data.model

import java.time.LocalDateTime

data class Item(
    val id: String,
    val name: String,
    val status: String,        // "open" | "done"
    val createdBy: String,
    val createdAt: LocalDateTime,
    val completedAt: LocalDateTime? = null,
    val reminderAt: LocalDateTime? = null,
)

data class User(
    val uid: String,
    val displayName: String,
    val role: UserRole,
    val pairId: String? = null,
)

enum class UserRole(val firestoreValue: String, val displayName: String) {
    MASTER("master", "はなこ（奥様）"),
    MEMBER("member", "ひろし（旦那）"),
}

data class PairInfo(
    val pairId: String,
    val masterName: String,
    val memberName: String,
    val inviteCode: String? = null,
)
