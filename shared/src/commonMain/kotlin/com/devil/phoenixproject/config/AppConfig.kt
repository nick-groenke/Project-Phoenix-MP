package com.devil.phoenixproject.config

object AppConfig {
    // Supabase
    const val SUPABASE_URL = "https://ilzlswmatadlnsuxatcv.supabase.co"
    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imlsemxzd21hdGFkbG5zdXhhdGN2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY4ODA5MzUsImV4cCI6MjA4MjQ1NjkzNX0._fNm07SvkCsMId2oBrg-Rf_5HCypTwLkjWu0T_5QizA"

    // RevenueCat - These are set per-platform
    object RevenueCat {
        const val ENTITLEMENT_PRO = "pro_access"
    }
}

// Platform-specific RevenueCat API keys
expect object PlatformConfig {
    val revenueCatApiKey: String
}
