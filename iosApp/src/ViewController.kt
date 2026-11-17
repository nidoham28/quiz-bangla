package io.aurafeed.quiz

import App
import androidx.compose.ui.window.ComposeUIViewController
import io.github.jan.supabase.auth.handleDeeplinks
import platform.Foundation.NSURL
import services.supabase.AuthRepository

fun ViewController() = ComposeUIViewController { App() }

fun handleSupabaseDeepLink(url: NSURL) {
    AuthRepository.supabaseClient.handleDeeplinks(url)
}
