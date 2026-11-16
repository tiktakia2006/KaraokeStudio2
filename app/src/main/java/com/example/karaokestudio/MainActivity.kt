package com.example.karaokestudio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.karaokestudio.ui.KaraokeAppRoot
import com.example.karaokestudio.ui.components.LoadingView
import com.example.karaokestudio.ui.theme.KaraokeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        val container = (application as KaraokeApp).container
        setContent {
            KaraokeTheme {
                val ready by container.seeder.isReady.collectAsStateWithLifecycle()
                if (ready) KaraokeAppRoot() else LoadingView(text = "Готуємо каталог…")
            }
        }
    }
}
