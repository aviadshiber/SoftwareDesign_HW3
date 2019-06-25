import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Duration

plugins {
    kotlin("jvm") version "1.3.31"
}

allprojects {
    repositories {
        jcenter()
        maven(url = "https://jitpack.io")
    }

    extra.apply {
        set("junitVersion", "5.4.2")
        set("hamkrestVersion", "1.7.0.0")
        set("guiceVersion", "4.2.2")
        set("kotlinGuiceVersion", "1.3.0")
        set("mockkVersion", "1.9.3")
        set("kotlinFuturesVersion", "1.2.0")
        set("dokkaVersion", "0.9.18")
    }
}

subprojects {
    apply(plugin = "kotlin")
    dependencies {
        val junitVersion: String? by extra
        val kotlinFuturesVersion: String? by extra
        val dokkaVersion: String? by extra
        implementation(kotlin("stdlib-jdk8"))
        compile(kotlin("reflect"))

        testRuntime("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

        // for completable future
        compile("com.github.vjames19.kotlin-futures", "kotlin-futures-jdk8", kotlinFuturesVersion)
        // for listenable future
        compile("com.github.vjames19.kotlin-futures", "kotlin-futures-guava", kotlinFuturesVersion)
        compile("org.jetbrains.dokka:dokka-android-gradle-plugin:$dokkaVersion")
        //compile("org.jetbrains.kotlin","kotlin-stdlib","1.2.21")
        
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
    tasks.withType<Test> {
        useJUnitPlatform()

        // Make sure tests don't take over 10 minutes
        timeout.set(Duration.ofMinutes(10))
    }
}

task<Zip>("submission") {
    val taskname = "submission"
    val base = project.rootDir.name
    archiveBaseName.set(taskname)
    from(project.rootDir.parentFile) {
        include("$base/**")
        exclude("$base/**/*.iml", "$base/*/build", "$base/**/.gradle", "$base/**/.idea", "$base/*/out",
                "$base/**/.git", "$base/**/.DS_Store")
        exclude("$base/$taskname.zip")
    }
    destinationDirectory.set(project.rootDir)
}