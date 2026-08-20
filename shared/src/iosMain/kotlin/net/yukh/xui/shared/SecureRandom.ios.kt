package net.yukh.xui.shared

import platform.posix.arc4random_uniform

// arc4random is Apple's CSPRNG — no seeding needed and it never blocks.
actual fun secureRandomIndex(bound: Int): Int = arc4random_uniform(bound.toUInt()).toInt()
