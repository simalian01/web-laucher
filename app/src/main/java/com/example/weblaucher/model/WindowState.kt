package com.example.weblaucher.model

data class WindowState(
    val windowId: String,
    val lastUrl: String,
    val lastScrollY: Int,
    val lastActiveTime: Long
)
