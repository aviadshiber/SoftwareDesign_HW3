package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Injector
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.tests.FakeCourseApp
import il.ac.technion.cs.softwaredesign.tests.TestModule
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger

open class CourseTest {
    protected val injector: Injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    protected var courseApp = injector.getInstance<CourseApp>()
    protected open var messageFactory = injector.getInstance<MessageFactory>()

    protected var bots = injector.getInstance<CourseBots>()

    init {
        bots.prepare().join()
        bots.start().join()
    }

    @AfterEach
    fun clearDb() {
        FakeStorage("botNames".toByteArray()).clear()
        FakeStorage("botsNameToData".toByteArray()).clear()
        FakeStorageFactory.names.clear()
        Logger.getGlobal().fine("test finished")
    }

    protected open fun reboot() {}

    protected fun doReboot() {
        val courseAppBefore: CourseApp = courseApp
        bots.bots().join()
        val injector: Injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())
        courseApp = injector.getInstance()
        (courseApp as FakeCourseApp).restore(courseAppBefore as FakeCourseApp)
        bots = injector.getInstance()
        bots.start().join()
        messageFactory = injector.getInstance()
    }


    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpLogger() {
            val logger = Logger.getGlobal()
            val fh = FileHandler("hw3.xml")
            logger.addHandler(fh)
            logger.level = Level.FINE
        }
    }
}