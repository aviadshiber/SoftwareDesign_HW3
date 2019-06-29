package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule

class CourseBotModule : KotlinModule() {
    override fun configure() {
        bind<CourseBots>().to<CourseBotsImpl>()
        bind<MutableMap<String, String>>().to<LinkedHashMap<String, String>>()
        bind<MutableSet<String>>().to<LinkedHashSet<String>>()
    }
}