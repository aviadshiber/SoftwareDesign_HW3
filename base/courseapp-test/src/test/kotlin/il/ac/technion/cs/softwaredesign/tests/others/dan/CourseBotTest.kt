import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.messages.MessageFactoryImpl
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.tests.FakeCourseApp
import il.ac.technion.cs.softwaredesign.tests.TestModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.CompletableFuture

class ByteArrayKey(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
            this === other || other is ByteArrayKey && this.bytes contentEquals other.bytes
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = bytes.contentToString()
}

class CourseBotTest {
    private val factoryFake = HashMap<ByteArrayKey, SecureStorage>()
//    private val dataStore: DataStore
    private var courseApp : CourseApp
    private var messageFactory: MessageFactory
    private var bots: CourseBots
    private var ssf : SecureStorageFactory = mockk<SecureStorageFactory>()
    init {
        every { ssf.open(any()) } answers {
            var ss = factoryFake.get(ByteArrayKey(firstArg()))
            if (ss == null) {
                val storageFake = HashMap<ByteArrayKey, ByteArrayKey>()
                ss = mockk<SecureStorage>()
                every { ss.read(any()) } answers { CompletableFuture.supplyAsync { storageFake.get(ByteArrayKey(firstArg()))?.bytes } }
                every { ss.write(any(), any()) } answers { CompletableFuture.supplyAsync { storageFake.put(ByteArrayKey(firstArg()), ByteArrayKey(secondArg())) }.thenApply { Unit } }
                factoryFake.put(ByteArrayKey(firstArg()), ss)
            }
            CompletableFuture.supplyAsync { ss }
        }
//        courseApp = CourseAppImpl(dataStore)
//        messageFactory = MessageFactoryImpl(ssf)
    }

    private var injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
        courseApp = injector.getInstance<CourseApp>()
        messageFactory = injector.getInstance<MessageFactory>()
        bots = injector.getInstance<CourseBots>()
    }

    init {
        bots.prepare().join()
        bots.start().join()
    }
// ================================================Our Tests============================================================

    @Test
    fun `Can list bot's channels by order of joining`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(adminToken, "#channel3").join()
        courseApp.channelJoin(adminToken, "#channel4").join()
        val bot = bots.bot().join()
        bot.join("#channel1").join()
        bot.join("#channel2").join()
        bot.join("#channel3").join()
        bot.join("#channel4").join()
        bot.part("#channel3").join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.channels().join()
        }, equalTo(listOf("#channel1", "#channel2", "#channel4")))
    }

    @Test
    fun `Can't join channel twice or non-existent channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()

        val bot = bots.bot().join()
        bot.join("#channel1").join()


        assertThrows<UserNotAuthorizedException> {
            bot.join("#channel1").joinException()
        }

        assertThrows<UserNotAuthorizedException> {
            bot.join("#impossible").joinException()
        }
    }

    @Test
    fun `Can't part channel twice or non-existent channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()

        val bot = bots.bot().join()
        bot.join("#channel1").join()
        bot.part("#channel1").join()

        assertThrows<NoSuchEntityException> {
            bot.part("#channel1").joinException()
        }

        assertThrows<NoSuchEntityException> {
            bot.part("#impossible").joinException()
        }
    }

    @Test
    fun `giving same count trigger resets the counter`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()

        bot.beginCount("#channel1", "bla[hH]", MediaType.TEXT).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()).join()
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", "bla[hH]", MediaType.TEXT).join()
        })
        bot.beginCount("#channel1", "bla[hH]", MediaType.TEXT).join()
        Assertions.assertEquals(0, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", "bla[hH]", MediaType.TEXT).join()
        })

        bot.beginCount(null, "bla[hH]", MediaType.TEXT).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()).join()
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, "bla[hH]", MediaType.TEXT).join()
        })
        bot.beginCount(null, "bla[hH]", MediaType.TEXT).join()
        Assertions.assertEquals(0, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, "bla[hH]", MediaType.TEXT).join()
        })

        bot.beginCount(null, null, MediaType.AUDIO).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.AUDIO, "blah".toByteArray()).join()).join()
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, null, MediaType.AUDIO).join()
        })
        bot.beginCount(null, null, MediaType.TEXT).join()
        Assertions.assertEquals(0, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, null, MediaType.TEXT).join()
        })
    }

    @Test
    fun `counting all media types works`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.beginCount(null, null, MediaType.AUDIO).join()
        bot.beginCount("#channel1", null, MediaType.AUDIO).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.AUDIO, "blah".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.AUDIO, "blubblub".toByteArray()).join()).join()
        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, null, MediaType.AUDIO).join()
        })
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.AUDIO, "blib".toByteArray()).join()).join()
        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", null, MediaType.AUDIO).join()
        })
    }

    @Test
    fun `setting parameter in count trigger to null makes it wildcard`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.beginCount(null, "[abc]", null).join()
        bot.beginCount("#channel1", null, MediaType.FILE).join()
        bot.beginCount("#channel2", "[a]", null).join()

        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.AUDIO, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.FILE, "b".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "c".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.PICTURE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "d".toByteArray()).join()).join()

        Assertions.assertEquals(4, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, "[abc]", null).join()
        })
        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", null, MediaType.FILE).join()
        })
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel2", "[a]", null).join()
        })
    }

    @Test
    fun `leaving channel resets channel specific regex counters`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.beginCount("#channel1", null, MediaType.FILE).join()
        bot.beginCount("#channel2", "[a]", null).join()

        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.FILE, "b".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "c".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.PICTURE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "d".toByteArray()).join()).join()

        bot.part("#channel1").join()

        Assertions.assertEquals(0, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", null, MediaType.FILE).join()
        })
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel2", "[a]", null).join()
        })

        bot.join("#channel1").join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()

        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel1", null, MediaType.FILE).join()
        })
        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count("#channel2", "[a]", null).join()
        })
    }

    @Test
    fun `leaving channel doesn't reset global regex counters`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.beginCount(null, null, MediaType.FILE).join()

        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.FILE, "b".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "c".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.PICTURE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "d".toByteArray()).join()).join()

        bot.part("#channel1").join()

        Assertions.assertEquals(4, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, null, MediaType.FILE).join()
        })

        bot.join("#channel1").join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()

        Assertions.assertEquals(6, runWithTimeout(Duration.ofSeconds(10)) {
            bot.count(null, null, MediaType.FILE).join()
        })
    }

    @Test
    fun `can't get count of count trigger we didn't start`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.beginCount(null, null, MediaType.FILE).join()
        bot.beginCount("#channel1", null, MediaType.TEXT).join()

        assertThrows<IllegalArgumentException> {
            bot.count(null, null, MediaType.PICTURE).joinException()
        }
        assertThrows<IllegalArgumentException> {
            bot.count("#channel1", "[abc]", MediaType.TEXT).joinException()
        }
        assertThrows<IllegalArgumentException> {
            bot.count(null, null, MediaType.TEXT).joinException()
        }
    }

    @Test
    fun `several bots should count a regex counter separately correctly`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        val bot2 = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()
        bot2.join("#channel1").join()
        bot2.join("#channel2").join()


        bot1.beginCount(null, null, MediaType.FILE).join()
        bot1.beginCount("#channel1", null, MediaType.TEXT).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.TEXT, "a".toByteArray()).join()).join()
        bot2.beginCount(null, null, MediaType.FILE).join()
        bot2.beginCount("#channel2", null, MediaType.TEXT).join()

        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.FILE, "a".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.TEXT, "b".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "c".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.TEXT, "a".toByteArray()).join()).join()

        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot1.count(null, null, MediaType.FILE).join()
        })
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot2.count(null, null, MediaType.FILE).join()
        })
        Assertions.assertEquals(1, runWithTimeout(Duration.ofSeconds(10)) {
            bot1.count("#channel1", null, MediaType.TEXT).join()
        })
        Assertions.assertEquals(2, runWithTimeout(Duration.ofSeconds(10)) {
            bot2.count("#channel2", null, MediaType.TEXT).join()
        })
    }

    @Test
    fun `can change calculator trigger`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()

        bot.setCalculationTrigger("calculate").join()
        val prevTrigger = bot.setCalculationTrigger("Gronk! Do the thing!").join()
        Assertions.assertEquals("calculate", prevTrigger)
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/6".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "Gronk! Do the thing! (2*30)/(5-3)".toByteArray()).join()).join()

        verify {
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()).toInt() == 30 })
        }
    }

    @Test
    fun `calculator gives correct result`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val bot = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot.join("#channel2").join()

        bot.setCalculationTrigger("calculate").join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/6".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/(5-3)".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.TEXT, "calculate (2*35+100/2)/(50-3*(4+6))".toByteArray()).join()).join()

        verify {
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel2@admin", any())
            listener.invoke("#channel1@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 10 })
            listener.invoke("#channel1@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 30 })
            listener.invoke("#channel2@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 6 })
        }
    }

    @Test
    fun `several bots will calculate separately`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot1 = bots.bot().join()
        val bot2 = bots.bot("Alexa").join()
        val bot3 = bots.bot().join()
        courseApp.addListener(adminToken, listener).join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()
        bot2.join("#channel1").join()
        bot2.join("#channel2").join()
        bot3.join("#channel2").join()

        bot1.setCalculationTrigger("calculate").join()
        bot2.setCalculationTrigger("calculate").join()
        bot3.setCalculationTrigger("calculate").join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/6".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel2", messageFactory.create(MediaType.TEXT, "calculate 1343235".toByteArray()).join()).join()

        verify {
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel2@admin", any())
            listener.invoke("#channel1@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 10 })
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()).toInt() == 10 })
            listener.invoke("#channel2@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 1343235 })
            listener.invoke("#channel2@Alexa", match { it.contents.toString(Charset.defaultCharset()).toInt() == 1343235 })
            listener.invoke("#channel2@Anna2", match { it.contents.toString(Charset.defaultCharset()).toInt() == 1343235 })
        }
    }

    @Test
    fun `disabling calculator should work`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()

        bot.setCalculationTrigger("calculate").join()
        val prevTrigger = bot.setCalculationTrigger(null).join()
        Assertions.assertEquals("calculate", prevTrigger)
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/6".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/(5-3)".toByteArray()).join()).join()

        verify {
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@admin", any())
        }
    }

    @Test
    fun `calculator only works on media type text`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        bot.join("#channel1").join()

        bot.setCalculationTrigger("calculate").join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.TEXT, "calculate (2*30)/6".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.AUDIO, "calculate (2*30)/(5-3)".toByteArray()).join()).join()
        courseApp.channelSend(adminToken, "#channel1", messageFactory.create(MediaType.STICKER, "calculate (2*30)/(5-3)".toByteArray()).join()).join()

        verify {
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@admin", any())
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()).toInt() == 10 })
        }
    }

    @Test
    fun `can change tipping trigger`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        val prevTrigger = bot.setTipTrigger("Gronk! Do the thing!").join()
        Assertions.assertEquals("tip", prevTrigger)
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()
        Assertions.assertNull(bot.richestUser("#channel1").join())
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "Gronk! Do the thing! 30 user2".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user2")))
    }

    @Test
    fun `disabling tipping should work`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()
        val prevTrigger = bot.setTipTrigger(null).join()
        Assertions.assertEquals("tip", prevTrigger)
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user2")))
    }

    @Test
    fun `tipping only works with text media type`() {
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.FILE, "tip 20 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user2")))
    }


    @Test
    fun `user can't tip more bits than he has`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 1001 user2".toByteArray()).join()).join()
        Assertions.assertNull(bot.richestUser("#channel1").join())
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 1000 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user1")))
    }

    @Test
    fun `user can't tip other user that isn't in channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 300 user2".toByteArray()).join()).join()
        Assertions.assertNull(bot.richestUser("#channel1").join())
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 100 user3".toByteArray()).join()).join()
        Assertions.assertNull(bot.richestUser("#channel1").join())
    }

    @Test
    fun `tips reset when bot leaves channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()

        bot.part("#channel1").join()
        bot.join("#channel1").join()

        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user1")))
    }

    @Test
    fun `richestUser should return null if their wasn't any tipping`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        Assertions.assertNull(bot.richestUser("#channel1").join())
    }

    @Test
    fun `richestUser should return null if more than one max`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user1".toByteArray()).join()).join()

        Assertions.assertNull(bot.richestUser("#channel1").join())
    }

    @Test
    fun `richestUser returns correct richest user`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot.join("#channel1").join()

        bot.setTipTrigger("tip").join()

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 400 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 1400 user3".toByteArray()).join()).join()
        courseApp.channelSend(token3, "#channel1", messageFactory.create(MediaType.TEXT, "tip 900 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 700 user1".toByteArray()).join()).join()
        courseApp.channelSend(token3, "#channel1", messageFactory.create(MediaType.TEXT, "tip 101 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot.richestUser("#channel1").join()
        }, present(equalTo("user1")))
    }

    @Test
    fun `different bots hold separate ledger for tips`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        val bot2 = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        bot1.join("#channel1").join()
        bot2.join("#channel1").join()

        bot1.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 500 user2".toByteArray()).join()).join()
        bot2.setTipTrigger("tip").join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.richestUser("#channel1").join()
        }, present(equalTo("user2")))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot2.richestUser("#channel1").join()
        }, present(equalTo("user1")))
    }

    @Test
    fun `different channels have separate tipping`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        bot1.setTipTrigger("tip").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 50 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user1".toByteArray()).join()).join()
        courseApp.channelSend(token1, "#channel2", messageFactory.create(MediaType.TEXT, "tip 25 user2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel2", messageFactory.create(MediaType.TEXT, "tip 55 user1".toByteArray()).join()).join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.richestUser("#channel1").join()
        }, present(equalTo("user2")))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.richestUser("#channel2").join()
        }, present(equalTo("user1")))
    }

    @Test
    fun `seen time returns correctly`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        Assertions.assertNull(bot1.seenTime("user1").join())

        val msg1 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg1).join()
        Assertions.assertEquals(msg1.created, bot1.seenTime("user1").join())
        val msg2 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel2", msg2).join()
        Assertions.assertEquals(msg2.created, bot1.seenTime("user1").join())
        val msg3 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token2, "#channel2", msg3).join()
        Assertions.assertEquals(msg3.created, bot1.seenTime("user2").join())
        Assertions.assertEquals(msg2.created, bot1.seenTime("user1").join())
    }

    @Test
    fun `bot only sees messages in channel he is in`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot1.join("#channel1").join()

        val msg1 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel2", msg1).join()
        val msg2 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel2", msg2).join()

        Assertions.assertNull(bot1.seenTime("user1").join())
    }

    @Test
    fun `bot only considers channel messages for last seen`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot1.join("#channel1").join()

        val msg1 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.privateSend(token1, "user2", msg1).join()
        val msg2 = messageFactory.create(MediaType.FILE, "blah".toByteArray()).join()
        courseApp.broadcast(adminToken, msg2).join()

        Assertions.assertNull(bot1.seenTime("user1").join())
    }

    @Test
    fun `seen time doesn't reset when bot leaves channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot1.join("#channel1").join()

        Assertions.assertNull(bot1.seenTime("user1").join())

        val msg1 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg1).join()
        Assertions.assertEquals(msg1.created, bot1.seenTime("user1").join())
        val msg2 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg2).join()
        Assertions.assertEquals(msg2.created, bot1.seenTime("user1").join())
        val msg3 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg3).join()
        Assertions.assertEquals(msg3.created, bot1.seenTime("user1").join())

        bot1.part("#channel1").join()

        Assertions.assertEquals(msg3.created, bot1.seenTime("user1").join())
    }

    @Test
    fun `different bots have separate seen time`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        val bot2 = bots.bot("Siri").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(adminToken, "#channel3").join()
        courseApp.channelJoin(token1, "#channel3").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()
        bot2.join("#channel2").join()
        bot2.join("#channel3").join()

        val msg1 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg1).join()
        val msg2 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel2", msg2).join()
        val msg3 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel3", msg3).join()
        Assertions.assertEquals(msg2.created, bot1.seenTime("user1").join())
        Assertions.assertEquals(msg3.created, bot2.seenTime("user1").join())
    }

    @Test
    fun `mostActiveUser return correctly`() {
        val adminToken = courseApp.login("admin", "supersecret").join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        courseApp.channelJoin(token3, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        Assertions.assertNull(bot1.mostActiveUser("#channel1").join())

        val msg1 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token1, "#channel1", msg1).join()
        var res = bot1.mostActiveUser("#channel1").join()
        Assertions.assertEquals("user1", res)

        val msg2 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token2, "#channel1", msg2).join()
        Assertions.assertNull(bot1.mostActiveUser("#channel1").join())

        val msg3 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token3, "#channel1", msg3).join()
        Assertions.assertNull(bot1.mostActiveUser("#channel1").join())

        val msg4 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token2, "#channel2", msg4).join()
        Assertions.assertNull(bot1.mostActiveUser("#channel1").join())
        Assertions.assertEquals("user2", bot1.mostActiveUser("#channel2").join())

        val msg5 = messageFactory.create(MediaType.TEXT, "blah".toByteArray()).join()
        courseApp.channelSend(token3, "#channel1", msg5).join()
        Assertions.assertEquals("user3", bot1.mostActiveUser("#channel1").join())
    }

    @Test
    fun `mostActiveUser throws exception if bot isn't in channel`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        assertThrows<NoSuchEntityException> {
            bot1.mostActiveUser("#impossible").joinException()
        }
    }

    @Test
    fun `single bot can have several separate identical surveys`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot1.join("#channel1").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        val survey2 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        val survey3 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(2L, 0L, 0L))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey2).join()
        }, containsElementsInOrder(1L, 0L, 0L))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey3).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        verify (exactly = 3) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `leaving channel resets survey and user votes`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot1.join("#channel1").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "2".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 1L, 0L))

        bot1.part("#channel1").join()
        bot1.join("#channel1").join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "3".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 0L, 1L))

        verify (exactly = 1) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `user can change his survey answer and gets one vote`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot1.join("#channel1").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "2".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 1L, 0L))

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "3".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(0L, 1L, 1L))

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "2".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "3".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(0L, 1L, 1L))

        verify (exactly = 1) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `survey count returns correctly`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot1.join("#channel1").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token3, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(3L, 0L, 0L))

        val token4 = courseApp.login("user4", "supersecret").join()
        courseApp.channelJoin(token4, "#channel1").join()

        courseApp.channelSend(token4, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(4L, 0L, 0L))

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "3".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "2".toByteArray()).join()).join()
        courseApp.channelSend(token4, "#channel1", messageFactory.create(MediaType.TEXT, "3".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 1L, 2L))

        verify (exactly = 1) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `several bots have separate surveys`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        val bot2 = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        bot1.join("#channel1").join()
        bot2.join("#channel1").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        val survey2 = bot2.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(2L, 0L, 0L))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot2.surveyResults(survey2).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        verify (exactly = 1) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
            listener.invoke("#channel1@Anna1", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `different channels have separate surveys`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val token3 = courseApp.login("user3", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(token3, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        courseApp.channelJoin(token3, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        val survey1 = bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        val survey2 = bot1.runSurvey("#channel2", "Gib Number", listOf("1", "2", "3")).join()
        courseApp.channelSend(token2, "#channel1", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        courseApp.channelSend(token2, "#channel2", messageFactory.create(MediaType.TEXT, "1".toByteArray()).join()).join()
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey1).join()
        }, containsElementsInOrder(2L, 0L, 0L))
        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bot1.surveyResults(survey2).join()
        }, containsElementsInOrder(1L, 0L, 0L))

        verify (exactly = 1) {
            listener.invoke("#channel1@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
            listener.invoke("#channel2@Alexa", match { it.contents.toString(Charset.defaultCharset()) == "Gib Number" })
        }
    }

    @Test
    fun `survey's throw correct errors on bad input`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot1 = bots.bot("Alexa").join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot1.join("#channel1").join()
        bot1.join("#channel2").join()

        assertThrows<NoSuchEntityException> {
            bot1.runSurvey("#impossible", "What is the meaning of life?", listOf("42")).joinException()
        }
        bot1.runSurvey("#channel1", "Gib Number", listOf("1", "2", "3")).join()
        assertThrows<NoSuchEntityException> {
            bot1.surveyResults("my survey results").joinException()
        }
    }

    @Test
    fun `should be able to get new instance of bot through CourseBots`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot.join("#channel1").join()
        bot.join("#channel2").join()
        bot.setTipTrigger("tip").join()
        bot.setCalculationTrigger("calculate").join()

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()

        val sameBot = bots.bot("Anna0").join()
        val prevTrigger = sameBot.setCalculationTrigger("calc").join()
        Assertions.assertEquals("calculate", prevTrigger)
        Assertions.assertEquals("user2", sameBot.richestUser("#channel1").join())
        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "calc 32".toByteArray()).join()).join()

        verify (exactly = 1) {
            listener.invoke("#channel1@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 32 })
        }

        sameBot.part("#channel1").join()
        sameBot.join("#channel1").join()
        Assertions.assertNull(sameBot.richestUser("#channel1").join())
    }

    @Test
    fun `bots setup on restart works`(){
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        val adminToken = courseApp.login("admin", "supersecret").join()
        courseApp.addListener(adminToken, listener).join()
        val token1 = courseApp.login("user1", "supersecret").join()
        val token2 = courseApp.login("user2", "supersecret").join()
        val bot = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(token1, "#channel1").join()
        courseApp.channelJoin(token2, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        courseApp.channelJoin(token1, "#channel2").join()
        courseApp.channelJoin(token2, "#channel2").join()
        bot.join("#channel1").join()
        bot.join("#channel2").join()
        bot.setTipTrigger("tip").join()
        bot.setCalculationTrigger("calculate").join()

        courseApp.channelSend(token1, "#channel1", messageFactory.create(MediaType.TEXT, "tip 20 user2".toByteArray()).join()).join()

        injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

        injector.getInstance<CourseAppInitializer>().setup().join()

//        val newDataStore = DataStoreImpl(ssf)
        val newCourseApp = injector.getInstance<CourseApp>()
        val newMessageFactory = injector.getInstance<MessageFactory>()
        val newCourseBots = injector.getInstance<CourseBots>()
        (newCourseApp as FakeCourseApp).restore(courseApp as FakeCourseApp)

        newCourseBots.start().join()
        newCourseApp.addListener(adminToken, listener).join()

        val sameBot = newCourseBots.bot("Anna0").join()
        val prevTrigger = sameBot.setCalculationTrigger("calc").join()
        Assertions.assertEquals("calculate", prevTrigger)
        Assertions.assertEquals("user2", sameBot.richestUser("#channel1").join())
        val msg1 = newMessageFactory.create(MediaType.TEXT, "calc 32".toByteArray()).join()
        newCourseApp.channelSend(token1, "#channel1", msg1).join()

        verify (exactly = 1) {
            listener.invoke("#channel1@Anna0", match {
                it.contents.toString(Charset.defaultCharset()).toInt() == 32
            })
        }

        sameBot.part("#channel1").join()
        sameBot.join("#channel1").join()
        Assertions.assertNull(sameBot.richestUser("#channel1").join())
    }


    @Test
    fun `can retrieve all bots created correctly`(){
        val adminToken = courseApp.login("admin", "supersecret").join()
        val bot1 = bots.bot().join()
        val bot2 = bots.bot("Alexa").join()
        val bot3 = bots.bot().join()
        val bot4 = bots.bot().join()
        courseApp.channelJoin(adminToken, "#channel1").join()
        courseApp.channelJoin(adminToken, "#channel2").join()
        bot1.join("#channel1").join()
        bot2.join("#channel2").join()
        bot3.join("#channel2").join()
        bot4.join("#channel2").join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bots().join()
        }, equalTo(listOf("Anna0", "Alexa", "Anna2", "Anna3")))

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bots("#channel1").join()
        }, equalTo(listOf("Anna0")))

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bots("#channel2").join()
        }, equalTo(listOf("Alexa", "Anna2", "Anna3")))
    }

// ==============================================Staff Tests============================================================

    @Test
    fun `Can create a bot and add make it join channels`() {
        val token = courseApp.login("gal", "hunter2").join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            val bot = courseApp.channelJoin(token, "#channel")
                    .thenCompose {
                        bots.bot()
                    }
                    .join()
            bot.join("#channel").join()
            bot.channels().join()
        }, equalTo(listOf("#channel")))
    }

    @Test
    fun `Can list bots in a channel`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
                }.join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>()
        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose {
                                                bot.setCalculationTrigger("calculate")
                                            }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose {
                                token ->
                                courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose {
                                token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose {
                                token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 + 2".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { it.contents.toString(Charset.defaultCharset()).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                        .thenCompose {
                                            bot -> bot.setTipTrigger("tip")
                                        }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose {
                                token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose {
                        it.richestUser("#channel")
                    }
                    .join()
        }, present(equalTo("gal")))
    }

    @Test
    fun `The bot accurately tracks keywords`() {
        val regex = ".*ello.*[wW]orl.*"
        val channel = "#channel"
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, channel)
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                                        .thenCompose { bot -> bot.beginCount(regex = regex) }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, channel).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, channel, messageFactory.create(MediaType.TEXT, "hello, world!".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(regex =  regex) }.join()
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = courseApp.login("gal", "hunter2")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val regularUserToken = courseApp.login("matan", "s3kr3t")
                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                .join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            val survey = bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                    listOf("Cranberry",
                            "Charcoal",
                            "Chocolate-chip Mint")).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(regularUserToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            courseApp.channelSend(adminToken, "#channel", messageFactory.create(MediaType.TEXT, "Chocolate-chip Mint".toByteArray()).join()).join()
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }
}