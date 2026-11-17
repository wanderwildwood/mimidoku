package com.wanderwildwood.mimidoku.playback

/**
 * Who may hold the player.
 *
 * The playback service has to be exported so the system, the media notification, Bluetooth and
 * the lock screen can reach it, and that also lets any app on the phone ask to connect. What is
 * playing is a book, which is nobody else's business, so a connection is accepted only from this
 * app, the system's own controllers (media notification, car), and a controller the system
 * vouches for (the system UI, a media button or Bluetooth, an app the person made a notification
 * listener). The three named commands (skip silence, boost, sleep timer) are for this app alone.
 */
internal object ControllerGate {

    fun accepts(ownPackage: String, packageName: String, trusted: Boolean, systemController: Boolean): Boolean =
        packageName == ownPackage || trusted || systemController

    fun mayUseNamedCommands(ownPackage: String, packageName: String): Boolean = packageName == ownPackage
}
