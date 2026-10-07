package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.preferences.preferencesModule
import org.koin.core.context.startKoin

private var isKoinStarted = false

/** Starts the dependency injection graph for iOS.
 *
 *  Unlike on Android, this only wires up a small subset of the app so far. A [Database]
 *  implementation now exists (SqliterDatabase.kt, via co.touchlab:sqliter) and is bound below,
 *  but most of the ~25 data modules that depend on it -- quests, notes, map data, edit history,
 *  achievements, ... -- haven't been individually checked yet for other Android-only
 *  dependencies (e.g. AssetManager/Resources bindings from Android's appModule, or
 *  QuestTypeRegistry/OverlayRegistry which are currently only populated by Android-only quest
 *  and overlay form modules), so they aren't included here yet.
 */
fun initIosKoin() {
    if (isKoinStarted) return
    isKoinStarted = true
    startKoin {
        modules(iosModule, preferencesModule)
    }
}
