val junitVersion = "5.5.0-M1"
val hamkrestVersion = "1.7.0.0"
val mockkVersion="1.9.3.kotlin12"
val guavaVersion="11.0.2"
val storageVersion="1.2"
plugins{
    //application
    id("org.jetbrains.dokka") version "0.9.18"
}

dependencies {
    compile("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", storageVersion)
    testCompile("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testCompile("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    // include JUnit 5 assertions
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    testCompile("com.natpryce:hamkrest:$hamkrestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    compile( "com.google.guava:guava: version=$guavaVersion")

    implementation("com.google.code.gson:gson:2.8.5")
    compile("io.reactivex.rxjava2", "rxjava" , "2.2.9")
    compile("io.reactivex.rxjava2", "rxkotlin", "2.3.0")
}

tasks.dokka{
    outputFormat = "html"
    outputDirectory = "$buildDir/javadoc"
}