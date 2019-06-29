package il.ac.technion.cs.softwaredesign.tests.others.avi

import com.authzee.kotlinguice4.KotlinModule
import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.google.inject.Inject
import com.google.inject.Injector
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import il.ac.technion.cs.softwaredesign.tests.containsElementsInOrder
import il.ac.technion.cs.softwaredesign.tests.joinException
import il.ac.technion.cs.softwaredesign.tests.runWithTimeout
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture


fun completedOf(): CompletableFuture<Unit> = CompletableFuture.completedFuture(Unit)
inline fun <reified T> completedOf(t: T): CompletableFuture<T> = CompletableFuture.completedFuture(t)
inline fun <reified T> failedOf(t: Throwable): CompletableFuture<T> = CompletableFuture.failedFuture(t)

class CourseBotTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp = mockk()
    private val statistics: CourseAppStatistics = mockk()
    private val messageFactory: MessageFactory = mockk(relaxed = true)
    private var bots: CourseBots

    // for bots
    class MockStorage : SecureStorage {
        private val encoding = Charsets.UTF_8

        private val keyvalDB = HashMap<String, ByteArray>()

        override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
            val bytes = keyvalDB[key.toString(encoding)]
            if (bytes != null)
                Thread.sleep(bytes.size.toLong())
            return CompletableFuture.completedFuture(bytes)
        }

        override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
            keyvalDB[key.toString(encoding)] = value
            return CompletableFuture.completedFuture(Unit)
        }
    }
    class SecureStorageFactoryMock @Inject constructor(val retvalue : SecureStorage) : SecureStorageFactory {
        override fun open(name : ByteArray) : CompletableFuture<SecureStorage> {
            return CompletableFuture.completedFuture(retvalue) // note: name unused
        }
    }
    init {

        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()

                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().toInstance(app)
                bind<CourseAppStatistics>().toInstance(statistics)
                bind<MessageFactory>().toInstance(messageFactory)

                bind<SecureStorage>().toInstance(MockStorage())
                bind<SecureStorageFactory>().to<SecureStorageFactoryMock>()
                bind<CourseBots>().to<CourseBotManager>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        bots = injector.getInstance()
        bots.start().join()
    }



    private fun newBots() : CourseBots {
        val bots : CourseBots = injector.getInstance()
        bots.start().join()
        return bots
    }



    @Test
    fun `bots exist after restarting`() {
        every { app.login(any(), any()) } returns completedOf("1")
        every { app.addListener(any(), any()) } returns completedOf()

        bots.bot("bot1").join()
        bots.bot("bot2").join()

        val newbots = newBots()

        assertThat(runWithTimeout(ofSeconds(10)) {
            newbots.bots().join()
        }, equalTo(listOf("bot1", "bot2")))
    }

    @Test
    fun `bots reuse old tokens if already logged in`() {
        every { app.login(any(), any()) } returns completedOf("token12345")
        every { app.addListener(any(), any()) } returns completedOf()
        every { app.channelJoin(any(), any()) } returns completedOf()

        bots.bot("bot1").join()

        every { app.login(any(), any()) } throws UserAlreadyLoggedInException()


        val bot = newBots().bot("bot1").join()
        bot.join("#test")

        verify(exactly = 1) {
            app.channelJoin("token12345","#test")
        }


    }


    @Nested
    inner class Channels {
        @Test
        fun `throws when can't join`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns failedOf(UserNotAuthorizedException())

            val bot = bots.bot().join()

            assertThrows<UserNotAuthorizedException> { bot.join("#WTF").joinException() }
        }

        @Test
        fun `throws when can't part`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelPart(any(), any()) } returns failedOf(NoSuchEntityException())

            val bot = bots.bot().join()

            assertThrows<NoSuchEntityException> { bot.part("#WTF").joinException() }
        }

        @Test
        fun `join and part`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()
            every { app.channelPart(any(), any()) } returns completedOf()

            val bot = bots.bot().join()
            bot.join("#c").join()

            assertDoesNotThrow { bot.part("#c").joinException() }
        }

        @Test
        fun `join only the asked channels`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()

            every { app.channelJoin("1", "#c1"  ) } returns completedOf()
            every { app.channelJoin("1", "#c7"  ) } returns completedOf()
            every { app.channelJoin("1", "#koko") } returns completedOf()

            bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c7") }
                    .thenCompose { bot.join("#koko") }
            }.join()

            verify(exactly = 1) {
                app.channelJoin("1","#c1"  )
                app.channelJoin("1","#c7"  )
                app.channelJoin("1","#koko")
            }

            confirmVerified()
        }

        @Test
        fun `list channels`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()

            val theBot = bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c2") }
                    .thenCompose { bot.join("#c22") }
                    .thenCompose { bot.join("#c14") }
                    .thenApply { bot }
            }.join()

            // another bot
            bots.bot().thenCompose { bot ->
                bot.join("#c1")
                    .thenCompose { bot.join("#c22222") }
                    .thenCompose { bot.join("#c2111112") }
                    .thenCompose { bot.join("#c14") }
                    .thenApply { bot }
            }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                theBot.channels().join()
            }, equalTo(listOf("#c1", "#c2", "#c22", "#c14")))
        }
        @Test
        fun `list channels after restart`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()

            val theBot = bots.bot("bot1").thenCompose { bot ->
                bot.join("#c1")
                        .thenCompose { bot.join("#c2") }
                        .thenCompose { bot.join("#c22") }
                        .thenCompose { bot.join("#c14") }
                        .thenApply { bot }
            }.join()


            theBot.channels().join()

            val samebot = newBots().bot("bot1").join()
            assertThat(runWithTimeout(ofSeconds(10)) {
                samebot.channels().join()
            }, equalTo(listOf("#c1", "#c2", "#c22", "#c14")))
        }

    }


    @Nested
    inner class Counter {

        @Test
        fun `beginCount throws IllegalArgumentException on bad input`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()

            val bot = bots.bot().join()

            // TODO: should it throw?
            assertThrows<IllegalArgumentException> {
                bot.beginCount("#hh", null, null).joinException()
            }

            every { app.channelJoin(any(), any()) } returns completedOf()
            bot.join("#hh").join()

            assertThrows<IllegalArgumentException> {
                bot.beginCount(null, null, null).joinException()
            }
        }

        @Test
        fun `count throws IllegalArgumentException without prior begin`() {
            every { app.login(any(), any()) } returns completedOf("1")
            every { app.addListener(any(), any()) } returns completedOf()

            val bot = bots.bot().join()

            assertThrows<IllegalArgumentException> {
                bot.count("#hh", "null", null).joinException()
            }

            every { app.channelJoin(any(), any()) } returns completedOf()
            bot.join("#hh").join()
            bot.beginCount("#hh", "null", null).joinException()

            assertThrows<IllegalArgumentException> {
                bot.count("#hdh", "null", null).joinException()
            }
            assertThrows<IllegalArgumentException> {
                bot.count("#hh", "null", MediaType.TEXT).joinException()
            }
            assertThrows<IllegalArgumentException> {
                bot.count("#hh", null, MediaType.TEXT).joinException()
            }
        }

        @Test
        fun `count with exact specs`() {
            val listener = slot<ListenerCallback>()
            val listeners = mutableListOf<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin("1", "#ch") } returns completedOf()
            every { app.addListener("1", capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            every { msg.id } returns 35
            listeners.forEach { it("#ch@someone", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(2L))
        }

        @Test
        fun `count works after restart`() {
            val listener = slot<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin("1", "#ch") } returns completedOf()
            every { app.addListener("1", capture(listener)) } answers {
                completedOf()
            }

            var bot = bots.bot("bot1").join()
            bot.join("#ch")
                    .thenCompose { bot.beginCount("#ch", "take.*me", MediaType.TEXT) }
                    .join()

            // Send message to old bot that he will count
            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 33
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // this should overwrite the listener
            bot = newBots().bot("bot1").join()

            // should be counted
            every { msg.id } returns 34
            every { msg.contents } returns "klj k take !!!2!! me !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // should not be counted
            every { msg.id } returns 35
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listener.invoke("#ch@someone", msg).join()

            // one before new bot and one after new bot
            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "take.*me", MediaType.TEXT).join()
            }, equalTo(2L))

        }

        @Test
        fun `restart counter`() {
            val listener = slot<ListenerCallback>()
            val listeners = mutableListOf<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin(any(), any()) } returns completedOf()
            every { app.addListener(any(), capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            bot.beginCount("#ch", "מחט", MediaType.TEXT).join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(0L))
        }

        @Test
        fun `counter counts after reset`() {
            val listener = slot<ListenerCallback>()
            val listeners = mutableListOf<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin(any(), any()) } returns completedOf()
            every { app.addListener(any(), capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            val msg = mockk<Message>(relaxed = true)
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "there is מחט here".toByteArray()

            val bot = bots.bot().join()
            bot.join("#ch")
                    // start counting
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                    // send 3 messages
                .thenApply { listeners.forEach { it("#ch@someone", msg).join() } }
                .thenApply { every { msg.id } returns 111 }
                .thenApply { listeners.forEach { it("#ch@someone", msg).join() } }
                .thenApply { every { msg.id } returns 423 }
                .thenApply { listeners.forEach { it("#ch@someone", msg).join() } }
                    // reset counter
                .thenCompose { bot.beginCount("#ch", "מחט", MediaType.TEXT) }
                    // send 2 messages
                .thenApply { every { msg.id } returns 343 }
                .thenApply { listeners.forEach { it("#ch@someoneElse", msg).join() } }
                .thenApply { every { msg.id } returns 322 }
                .thenApply { listeners.forEach { it("#ch@someoneWho", msg).join() } }
                .join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "מחט", MediaType.TEXT).join()
            }, equalTo(2L))
        }

        @Test
        fun `count with regex`() {
            val listener = slot<ListenerCallback>()
            val listeners = mutableListOf<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin("1", "#ch") } returns completedOf()
            every { app.addListener("1", capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            val bot = bots.bot().join()
            bot.join("#ch")
                .thenCompose { bot.beginCount("#ch", "take.*me", MediaType.TEXT) }
                .join()

            val msg = mockk<Message>(relaxed = true)

            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            every { msg.id } returns 35
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listeners.forEach { it("#ch@someone", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count("#ch", "take.*me", MediaType.TEXT).join()
            }, equalTo(1L))
        }

        @Test
        fun `count with regex all channels`() {
            val listener = slot<ListenerCallback>()
            val listeners = mutableListOf<ListenerCallback>()

            every { app.login(any(), any()) } returns completedOf("1")
            every { app.channelJoin("1", any()) } returns completedOf()
            every { app.addListener("1", capture(listener)) } answers {
                listeners.add(listener.captured)
                completedOf()
            }

            val bot = bots.bot()
                .thenCompose {bot ->
                    bot.join("#ch1")
                        .thenCompose { bot.join("#ch2") }
                        .thenCompose { bot.beginCount(null, "take.*me", MediaType.TEXT) }
                        .thenApply { bot }
                }.join()

            val msg = mockk<Message>(relaxed = true)

            // count this - #1
            every { msg.id } returns 34
            every { msg.media } returns MediaType.TEXT
            every { msg.contents } returns "klj k take !!!!! me !!!".toByteArray()
            listeners.forEach { it("#ch1@jjj", msg).join() }

            // count this - #2
            every { msg.id } returns 35
            listeners.forEach { it("#ch2@iii", msg).join() }

            // DON'T count this
            every { msg.id } returns 36
            every { msg.contents } returns "klj k e !!!!! me take !!!".toByteArray()
            listeners.forEach { it("#ch1@kkk", msg).join() }

            assertThat(runWithTimeout(ofSeconds(10)) {
                bot.count(null, "take.*me", MediaType.TEXT).join()
            }, equalTo(2L))
        }
    }

    @Nested
    inner class Calculator {

    }

    @Nested
    inner class Tipping {

    }

    @Nested
    inner class Stocker {

    }

    @Nested
    inner class Survey {

    }

    @Nested
    inner class General {
        //TODO: from bots.bots: @return List of bot names **in order of bot creation.**
        @Test
        fun `default name`() {
            every { app.login("Anna0", any()) } returns completedOf("1")
            every { app.login("Anna1", any()) } returns completedOf("2")
            every { app.login("Anna2", any()) } returns completedOf("3")

            every { app.addListener("1", any()) } returns completedOf()
            every { app.addListener("2", any()) } returns completedOf()
            every { app.addListener("3", any()) } returns completedOf()

            every { app.channelJoin("1", any()) } returns completedOf()
            every { app.channelJoin("2", any()) } returns completedOf()
            every { app.channelJoin("3", any()) } returns completedOf()

            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot("Anna0").thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots("#c1").join()
            }, equalTo(listOf("Anna0", "Anna1", "Anna2")))

            verify(atLeast = 3) {
                app.login(any(), any())
                app.addListener(any(), any())
                app.channelJoin(any(), any())
            }

            confirmVerified()
        }

        @Test
        fun `custom name`() {
            every { app.login("beni sela", any()) } returns completedOf("1")

            every { app.addListener("1", any()) } returns completedOf()
            every { app.channelJoin("1", any()) } returns completedOf()

            bots.bot("beni sela").thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots("#c1").join()
            }, equalTo(listOf("beni sela")))

            verify(atLeast = 1) {
                app.login(any(), any())
                app.addListener(any(), any())
                app.channelJoin(any(), any())
            }

            confirmVerified()
        }

        @Test
        fun `list bots from all channels`() {
            every { app.login("Anna0", any()) } returns completedOf("1")
            every { app.login("Anna1", any()) } returns completedOf("2")
            every { app.login("Anna2", any()) } returns completedOf("3")
            every { app.login("Anna3", any()) } returns completedOf("4")

            every { app.addListener(any(), any()) } returns completedOf()
            every { app.channelJoin(any(), any()) } returns completedOf()

            bots.bot().thenCompose { it.join("#c1") }.join()
            bots.bot().thenCompose { it.join("#c2") }.join()
            bots.bot().thenCompose { it.join("#c1") }.join()

            assertThat(runWithTimeout(ofSeconds(10)) {
                bots.bots().join()
            }, equalTo(listOf("Anna0", "Anna1", "Anna2")))
        }
    }
}

class CourseBotStaffTest {
    // We Inject a mocked KeyValueStore and not rely on a KeyValueStore that relies on another DB layer
    private val injector: Injector
    private val app: CourseApp // TODO: use mock instead of our full impl?
    private val statistics: CourseAppStatistics // TODO: same
    private val messageFactory: MessageFactory // TODO: same
    private val bots: CourseBots

    init {
        class CourseAppModuleMock : KotlinModule() {
            override fun configure() {
                val keystoreinst = VolatileKeyValueStore()

                val managers = Managers(keystoreinst)
                bind<Managers>().toInstance(managers)
                bind<MessageFactory>().toInstance(managers.messages)

                bind<KeyValueStore>().toInstance(keystoreinst)
                bind<CourseApp>().to<CourseAppImpl>()
                bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()


                // Bots
                class MockStorage : SecureStorage {
                    private val encoding = Charsets.UTF_8

                    private val keyvalDB = HashMap<String, ByteArray>()

                    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
                        val bytes = keyvalDB[key.toString(encoding)]
                        if (bytes != null)
                            Thread.sleep(bytes.size.toLong())
                        return CompletableFuture.completedFuture(bytes)
                    }

                    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
                        keyvalDB[key.toString(encoding)] = value
                        return CompletableFuture.completedFuture(Unit)
                    }
                }

                class SecureStorageFactoryMock : SecureStorageFactory {
                    override fun open(name : ByteArray) : CompletableFuture<SecureStorage> {
                        return CompletableFuture.completedFuture(MockStorage())
                    }
                }
                bind<SecureStorageFactory>().to<SecureStorageFactoryMock>()
                bind<CourseBots>().to<CourseBotManager>()
            }
        }

        injector = Guice.createInjector(CourseAppModuleMock())
        app = injector.getInstance()
        statistics = injector.getInstance()
        messageFactory = injector.getInstance()
        bots = injector.getInstance()

        bots.start()
    }

    @Test
    fun `Can create a bot and add make it join channels`() {
        val token = app.login("gal", "hunter2").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val bot = app.channelJoin(token, "#channel")
                .thenCompose { bots.bot() }
                .join()
            bot.join("#channel").join()
            bot.channels().join()
        }, equalTo(listOf("#channel")))
    }

    @Test
    fun `Can list bots in a channel`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose { bots.bot().thenCompose { it.join("#channel") } }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>(relaxed = true)

        every { listener(any(), any()) } returns CompletableFuture.completedFuture(Unit)

        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot().thenCompose { bot ->
                            bot.join("#channel")
                                .thenApply { bot.setCalculationTrigger("calculate") }
                        }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.addListener(token, listener)
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "calculate 20 * 2 + 2".toByteArray()).join())
                    }
            }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0",
                            match { it.contents.toString(Charsets.UTF_8).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot()
                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                            .thenCompose { bot -> bot.setTipTrigger("tip") }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "tip 10 gal".toByteArray()).join())
                    }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                .thenCompose { it.richestUser("#channel") }
                .join()
        }, present(equalTo("gal")))
    }

    @Test
    fun `The bot accurately tracks keywords`() {
        app.login("gal", "hunter2")
            .thenCompose { adminToken ->
                app.channelJoin(adminToken, "#channel")
                    .thenCompose {
                        bots.bot()
                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                            .thenCompose { bot -> bot.beginCount(null, ".*ello.*[wW]orl.*") }
                    }
                    .thenCompose { app.login("matan", "s3kr3t") }
                    .thenCompose { token ->
                        app.channelJoin(token, "#channel")
                            .thenApply { token }
                    }
                    .thenCompose { token ->
                        app.channelSend(token,
                                        "#channel",
                                        messageFactory.create(MediaType.TEXT,
                                                              "hello, world!".toByteArray()).join())
                    }
            }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(null, ".*ello.*[wW]orl.*") }
                .join() // missing null for channel
        }, equalTo(1L))
    }

    @Test
    fun `A user in the channel can ask the bot to do a survey`() {
        val adminToken = app.login("gal", "hunter2")
            .thenCompose { token -> app.channelJoin(token, "#channel").thenApply { token } }
            .join()
        val regularUserToken = app.login("matan", "s3kr3t")
            .thenCompose { token -> app.channelJoin(token, "#channel").thenApply { token } }
            .join()
        val bot = bots.bot()
            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
            .join()

        assertThat(runWithTimeout(ofSeconds(100000)) {
            val survey =
                    bot.runSurvey("#channel", "What is your favorite flavour of ice-cream?",
                                  listOf("Cranberry",
                                         "Charcoal",
                                         "Chocolate-chip Mint")).join()
            app.channelSend(adminToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            app.channelSend(regularUserToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            app.channelSend(adminToken,
                            "#channel",
                            messageFactory.create(MediaType.TEXT,
                                                  "Chocolate-chip Mint".toByteArray()).join())
            bot.surveyResults(survey).join()
        }, containsElementsInOrder(0L, 0L, 2L))
    }
}
