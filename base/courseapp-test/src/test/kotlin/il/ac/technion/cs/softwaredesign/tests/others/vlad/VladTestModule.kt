package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.authzee.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import il.ac.technion.cs.softwaredesign.tests.others.vlad.FakeStorageFactory
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.messages.MessageImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.tests.FakeCourseApp
import il.ac.technion.cs.softwaredesign.tests.FakeCourseAppInitializer

class VladTestModule : KotlinModule() {
    override fun configure() {
        bind<SecureStorageFactory>().toInstance(FakeStorageFactory())
//        bind<SecureStorageFactory>().toInstance(SecureHashMapStorageFactoryImpl())
        bind<MessageFactory>().to<MessageFactoryImpl>()
        bind<Message>().to<MessageImpl>()
        bind<CourseAppInitializer>().to<FakeCourseAppInitializer>()
        bind<CourseApp>().to<FakeCourseApp>()
    }
}