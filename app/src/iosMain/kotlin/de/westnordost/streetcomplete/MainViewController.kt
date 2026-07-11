package de.westnordost.streetcomplete

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.ui.theme.AppTheme
import platform.UIKit.UIViewController

/** Entry point called from iosApp/ContentView.swift via `MainViewControllerKt.MainViewController()`.
 *
 *  This currently shows a placeholder screen: the app's real entry point (MainActivity) is still
 *  built on Android-only Fragments and the MapLibre Android SDK, neither of which is ported to
 *  iOS yet. This wires up the Compose Multiplatform <-> UIKit bridge so the framework actually
 *  has something to embed and run.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    AppTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("StreetComplete", style = MaterialTheme.typography.h4)
                Text("iOS build placeholder — the full app UI is not ported yet")
            }
        }
    }
}
