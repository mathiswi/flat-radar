plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.serialization") version "2.1.20" apply false
}

allprojects {
    group = "com.flatradar"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}
