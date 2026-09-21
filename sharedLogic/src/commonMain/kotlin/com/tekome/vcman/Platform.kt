package com.tekome.vcman

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform