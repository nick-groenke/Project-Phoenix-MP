package com.devil.phoenixproject.config

object AppConfig {
    // RevenueCat - These are set per-platform
    object RevenueCat {
        const val ENTITLEMENT_PRO = "pro_access"
    }
}

// Platform-specific RevenueCat API keys
expect object PlatformConfig {
    val revenueCatApiKey: String
}
