val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val kotlinFuturesVersion: String? by extra

dependencies {
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", "1.2")
    compile("com.google.code.gson", "gson", "2.8.5")
    compile("com.github.salomonbrys.kotson", "kotson", "2.5.0")


    testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testCompile("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testCompile("com.natpryce", "hamkrest", hamkrestVersion)
    testCompile("com.google.inject", "guice", guiceVersion)
    testCompile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)

    testImplementation("io.mockk", "mockk", "1.9.3")

    runtime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    // for completable future
    compile("com.github.vjames19.kotlin-futures", "kotlin-futures-jdk8", kotlinFuturesVersion)
    // for listenable future
    compile("com.github.vjames19.kotlin-futures", "kotlin-futures-guava", kotlinFuturesVersion)
}