package io.aurafeed.quiz

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
