package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.messages.MessageImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.tests.FakeCourseApp
import il.ac.technion.cs.softwaredesign.tests.FakeCourseAppInitializer
import il.ac.technion.cs.softwaredesign.tests.SecureHashMapStorageFactoryImpl

class VladTestModule : KotlinModule() {
    override fun configure() {
        bind<MutableMap<String, String>>().to<LinkedHashMap<String, String>>()
        bind<MutableSet<String>>().to<LinkedHashSet<String>>()
        bind<SecureStorageFactory>().toInstance(FakeStorageFactory())
        bind<SecureStorageFactory>().toInstance(SecureHashMapStorageFactoryImpl())
      //  bind<MessageFactory>().to<MessageFactoryImpl>()
        //bind<Message>().to<MessageImpl>()
        bind<CourseAppInitializer>().to<FakeCourseAppInitializer>()
        bind<CourseApp>().to<FakeCourseApp>()
        bind<MutableMap<String, String>>().to<LinkedHashMap<String, String>>()
        bind<MutableSet<String>>().to<LinkedHashSet<String>>()
    }
}