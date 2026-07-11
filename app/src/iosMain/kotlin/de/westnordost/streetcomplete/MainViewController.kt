package de.westnordost.streetcomplete

import androidx.compose.ui.window.ComposeUIViewController
import de.westnordost.streetcomplete.screens.settings.language_selection.LanguageSelectionScreen
import de.westnordost.streetcomplete.ui.theme.AppTheme
import org.koin.compose.viewmodel.koinViewModel
import platform.UIKit.UIViewController

/** Entry point called from iosApp/ContentView.swift via `MainViewControllerKt.MainViewController()`.
 *
 *  This does not show the real app yet: the app's real entry point (MainActivity) is still built
 *  on Android-only Fragments and the MapLibre Android SDK, and most of the data layer depends on
 *  a Database implementation that doesn't exist for iOS yet (see initIosKoin()). What's shown
 *  here -- language selection -- is one of the few screens that is both fully portable already
 *  and free of that Database dependency, so it's used here as a real (not mocked) proof that the
 *  Compose Multiplatform UI, Koin DI, and NSUserDefaults-backed preferences all actually work
 *  end to end on iOS.
 */
fun MainViewController(): UIViewController {
    initIosKoin()
    return ComposeUIViewController {
        AppTheme {
            LanguageSelectionScreen(
                viewModel = koinViewModel(),
                onClickBack = {},
            )
        }
    }
}
