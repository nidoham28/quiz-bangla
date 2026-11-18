package io.aurafeed.quiz

import App
import androidx.compose.ui.window.ComposeUIViewController
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL
import services.supabase.SupabaseService

fun ViewController() = ComposeUIViewController { App() }

fun handleSupabaseDeepLink(url: NSURL) {
    SupabaseService.instance.handleDeeplinks(url)
}
