package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.preferences.preferencesModule
import org.koin.core.context.startKoin

private var isKoinStarted = false

/** Starts the dependency injection graph for iOS.
 *
 *  Unlike [de.westnordost.streetcomplete] on Android, this currently only wires up the small
 *  subset of the app that doesn't depend on a database: there is no iOS implementation of
 *  [de.westnordost.streetcomplete.data.Database] yet (Android's is backed by
 *  android.database.sqlite; iOS would need a SQLite binding of its own). Almost everything else
 *  -- quests, notes, map data, edit history, achievements, ... -- depends on it, directly or
 *  transitively, and so isn't included here yet.
 */
fun initIosKoin() {
    if (isKoinStarted) return
    isKoinStarted = true
    startKoin {
        modules(iosModule, preferencesModule)
    }
}
