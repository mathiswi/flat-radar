plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.jsoup)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.dotenv.java)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "dev.flatradar.scraper.MainKt"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
