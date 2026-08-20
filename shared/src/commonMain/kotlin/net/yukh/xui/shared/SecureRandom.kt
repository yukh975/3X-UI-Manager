package net.yukh.xui.shared

/**
 * A cryptographically secure random index in `[0, bound)`.
 *
 * Used for identifiers the panel itself mints with `window.crypto.getRandomValues`
 * — a subscription id grants access to a client's whole configuration, so an
 * ordinary PRNG would be a downgrade in strength.
 */
expect fun secureRandomIndex(bound: Int): Int
