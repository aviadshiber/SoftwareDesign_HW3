package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.messages.MessageImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class TestModule : KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().toInstance(SecureHashMapStorageFactoryImpl())
        bind<MessageFactory>().to<MessageFactoryImpl>()
        bind<Message>().to<MessageImpl>()
        bind<CourseAppInitializer>().to<FakeCourseAppInitializer>()
        bind<CourseApp>().to<FakeCourseApp>()
        bind<MutableMap<String, String>>().to<LinkedHashMap<String, String>>()
        bind<MutableSet<String>>().to<LinkedHashSet<String>>()

    }
}