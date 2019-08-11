
val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val mockkVersion: String? by extra

dependencies {
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", "1.2")

    testCompile("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testCompile("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testCompile("com.natpryce", "hamkrest", hamkrestVersion)
    testCompile("com.google.inject", "guice", guiceVersion)
    testCompile("com.authzee.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)
}