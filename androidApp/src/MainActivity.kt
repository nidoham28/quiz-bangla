package io.aurafeed.quiz

import App
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.jan.supabase.auth.handleDeeplinks
import services.supabase.AuthRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_VIEW) {
            AuthRepository.supabaseClient.handleDeeplinks(intent)
        }
        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            AuthRepository.supabaseClient.handleDeeplinks(intent)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
