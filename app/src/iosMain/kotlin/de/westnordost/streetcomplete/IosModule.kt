package de.westnordost.streetcomplete

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.screens.settings.language_selection.LanguageSelectionViewModel
import de.westnordost.streetcomplete.screens.settings.language_selection.LanguageSelectionViewModelImpl
import de.westnordost.streetcomplete.util.sound.IosSoundEffectPlayer
import de.westnordost.streetcomplete.util.sound.SoundEffectPlayer
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

val iosModule = module {

    // Settings

    single<ObservableSettings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }

    // sound

    single<SoundEffectPlayer> {
        val dir = NSBundle.mainBundle.resourcePath + "/compose-resources/files"
        IosSoundEffectPlayer(dir)
    }

    // bundled resources (yaml, strings, ...), mirrors the Android-only binding in ApplicationModule
    single<Res> { Res }

    // screens that are already fully portable (i.e. don't need the not-yet-implemented iOS
    // Database) can be registered here as they get proven out. See initIosKoin() for the
    // current, much shorter, list of modules that are actually safe to start on iOS.
    viewModel<LanguageSelectionViewModel> { LanguageSelectionViewModelImpl(get(), get()) }
}
