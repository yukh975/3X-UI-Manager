package net.yukh.xui.shared

/**
 * The device's UI language as a two-letter code (`en`, `ru`, …).
 *
 * The app follows it unless the user picked a language explicitly, so a Russian
 * phone gets a Russian app without visiting Settings first.
 */
expect fun systemLanguage(): String
