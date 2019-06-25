package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.services.CourseBotApi

class CourseBotModule : KotlinModule() {
    override fun configure() {
        bind<CourseBots>().to<CourseBotsImpl>()
        bind<CourseBot>().to<CourseBotImpl>()
        bind<CourseBotApi>().to<CourseBotApi>() //explicit
        bind<Database>().to<Database>() //explicit
    }
}