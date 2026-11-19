package com.wanderwildwood.mimidoku.server

import android.content.Context
import com.wanderwildwood.mimidoku.data.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This app's side of the Nextcloud account Home holds for the phone ([HomeAccount]).
 *
 * What is kept is only the reader's choice to use it: who (server and user) and the stamp last
 * taken, in [Preferences.nextcloudHomeWho] and [Preferences.nextcloudHomeStamp]. The password is
 * asked of Home and held here, in memory, and nowhere else: it is never written to the
 * preferences, so there is no copy to leave behind when Home signs out.
 *
 * Home is asked on every start and resume of the app, on each change notice from Home, when the
 * Nextcloud screen opens, and before each read of the folder or download of a book.
 */
object HomeLink {

    /** Home's app password while its account is in use here; empty otherwise. Never stored, never logged. */
    @Volatile
    var password: String = ""
        private set

    /**
     * Asks Home (off the main thread) and does what [HomeAccount.follow] says, when the reader
     * chose Home's account. Returns Home's answer, so the screen knows whether to offer the row.
     * [stopDownloads] is false when the download service itself asks, before a book.
     */
    suspend fun refresh(context: Context, stopDownloads: Boolean = true): HomeAccount.Answer {
        val answer = withContext(Dispatchers.IO) { HomeAccount.ask(context.applicationContext) }
        withContext(Dispatchers.Main.immediate) { follow(context, answer, stopDownloads) }
        return answer
    }

    private fun follow(context: Context, answer: HomeAccount.Answer, stopDownloads: Boolean) {
        val prefs = Preferences.of(context)
        val who = prefs.nextcloudHomeWho
        if (who.isEmpty()) {
            password = ""
            return
        }
        when (val step = HomeAccount.follow(who, prefs.nextcloudHomeStamp, answer)) {
            // Nothing changed. After a fresh start the password is not in memory yet: same stamp, same password.
            is HomeAccount.Step.Keep -> (answer as? HomeAccount.Answer.Has)?.let { password = it.held.password }
            is HomeAccount.Step.Take -> take(prefs, step.held)
            is HomeAccount.Step.Drop -> drop(context, prefs, following = step.following, stopDownloads)
        }
    }

    /**
     * The reader pressed "Use the phone's Nextcloud account": Home's account replaces the app's
     * own sign-in, whose sealed password goes. Null when Home has no account for this app now.
     */
    suspend fun use(context: Context): HomeAccount.Held? {
        val held = withContext(Dispatchers.IO) { HomeAccount.read(context.applicationContext) } ?: return null
        withContext(Dispatchers.Main.immediate) {
            val prefs = Preferences.of(context)
            if (HomeAccount.who(prefs.nextcloudUrl, prefs.nextcloudLogin) != held.who) prefs.nextcloudUserId = ""
            prefs.nextcloudPassword = ""
            prefs.nextcloudHomeWho = held.who
            take(prefs, held)
        }
        return held
    }

    /**
     * "Stop using it": who, stamp and password are forgotten and the app's own sign-in is shown,
     * empty. The books already on the phone stay.
     */
    fun stop(context: Context) {
        val prefs = Preferences.of(context)
        password = ""
        prefs.nextcloudHomeWho = ""
        prefs.nextcloudHomeStamp = ""
        prefs.nextcloudUrl = ""
        prefs.nextcloudLogin = ""
        prefs.nextcloudPassword = ""
        prefs.nextcloudUserId = ""
    }

    private fun take(prefs: Preferences, held: HomeAccount.Held) {
        password = held.password
        prefs.nextcloudUrl = held.server.trimEnd('/')
        prefs.nextcloudLogin = held.user
        // Sign in with the login name; build every path with the user id, which may differ (an
        // email address logs in too). An older Home has no uid column, and then Held.uid is the
        // login name: the id this app finds itself ([NextcloudClient.userId]) is used instead.
        if (held.uid != held.user) prefs.nextcloudUserId = held.uid
        prefs.nextcloudHomeStamp = held.stamp
    }

    /**
     * Home signed out, is gone, or now holds another account: the password goes, the app shows
     * itself signed out and stops fetching from Nextcloud. What is on the phone stays. Following,
     * who is kept with an empty stamp, so the same account is taken again when Home has it back;
     * not following (another account), who goes too and the app's own sign-in shows, empty.
     */
    private fun drop(context: Context, prefs: Preferences, following: Boolean, stopDownloads: Boolean) {
        password = ""
        prefs.nextcloudHomeStamp = ""
        if (!following) stop(context)
        if (stopDownloads && KeepService.now.value?.bookUri?.startsWith(NextcloudSync.bookUri("")) == true) {
            runCatching { KeepService.stop(context) }
        }
    }
}
