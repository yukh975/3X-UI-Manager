package net.yukh.xui.shared

import java.security.SecureRandom

private val secureRng = SecureRandom()

actual fun secureRandomIndex(bound: Int): Int = secureRng.nextInt(bound)
