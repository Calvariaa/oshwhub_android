package io.github.Calvariaa.oshwhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.Calvariaa.oshwhub.ui.AppRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OshApplication).container
        container.session.warmUp()
        setContent {
            AppRoot(container)
        }
    }
}
