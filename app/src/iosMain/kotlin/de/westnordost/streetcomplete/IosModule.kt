package de.westnordost.streetcomplete

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.createIosDatabase
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

    // Database, via co.touchlab:sqliter. See SqliterDatabase.kt.
    single<Database> { createIosDatabase(ApplicationConstants.DATABASE_NAME) }

    // screens that are already fully portable can be registered here as they get proven out.
    // See initIosKoin() for the current, much shorter, list of modules that are actually wired
    // up on iOS -- most of the app's data modules aren't included yet even though Database now
    // exists, since they haven't been individually checked for other Android-only dependencies.
    viewModel<LanguageSelectionViewModel> { LanguageSelectionViewModelImpl(get(), get()) }
}
