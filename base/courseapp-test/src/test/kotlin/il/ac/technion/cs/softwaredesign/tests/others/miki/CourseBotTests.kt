package il.ac.technion.cs.softwaredesign.tests.others.miki

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.lib.utils.thenForward
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.tests.TestModule
import il.ac.technion.cs.softwaredesign.tests.joinException
import il.ac.technion.cs.softwaredesign.tests.randomChannelName
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotMikiTests {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        bots.prepare().thenCompose { bots.start() }.join();
    }

    @Nested
    inner class JoinAndPartTests {

        @Test
        fun `A bot successfully joins a channel`() {
            val channel = "#channel"
            val channels = bots.bot("John")
                    .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                    .thenCompose { bot -> bot.channels() }.join()


            assertThat(channels, equalTo(listOf(channel)))
        }

        @Test
        fun `throws NoSuchEntityException if the channel cant be joined or parted`() {

            val channel = "#channel"
            val botName = "John"

            // Cant part a non-joined channel
            assertThrows<NoSuchEntityException> {
                bots.bot(botName)
                        .thenCompose { bot -> bot.part(channel) }
                        .joinException()
            }

            // Can't part a non-existing channel
            assertThrows<NoSuchEntityException> {
                bots.bot(botName)
                        .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                        .thenCompose { bot -> bot.part("illegal") }
                        .joinException()
            }

            // Can't part an already parted channel
            assertThrows<NoSuchEntityException> {
                bots.bot(botName)
                        .thenCompose { bot ->
                            bot.join(channel)
                                    .thenApply { bot }
                                    .thenForward(bot.part(channel))
                                    .thenForward(bot.part(channel))
                        }
                        .joinException()
            }
        }

        @Test
        fun `A bot successfully leaves a channel`() {
            val channel = "#channel"
            val channels = bots.bot("John")
                    .thenCompose { bot -> bot.join(channel).thenApply { bot } }
                    .thenCompose { bot -> bot.part(channel).thenApply { bot } }
                    .thenCompose { bot -> bot.channels() }.join()

            assertThat(channels, equalTo(listOf()))
        }
    }

    @Test
    fun `Returns the right amount of channels per bot`() {
        val numChannels = (1..20).shuffled().first()

        val bot = bots.bot("Join").join()
        val channelSet: MutableSet<String> = mutableSetOf()

        for (i in (1..numChannels)) {
            val name = randomChannelName()
            bot.join(name).thenApply { channelSet.add(name) }.join()
        }

        assertThat(bot.channels().join().toSet(), equalTo(channelSet.toSet()))
    }

    @Nested
    inner class CountTests {

        @Test
        fun `throws IllegalArgumentException if regex and mediaType are both null`() {

            val bot = bots.bot("Join").join()
            val channel1 = "#channel1"

            assertThrows<IllegalArgumentException> {
                bot.beginCount(channel1).joinException()
            }
        }

        @Test
        fun `throws IllegalArgumentException if a begin count has not been registered with the provided arguments`() {
            val bot = bots.bot("Join").join()
            val channel1 = "#channel1"

            assertThrows<IllegalArgumentException> {
                bot.count(channel1).joinException()
            }
        }

        @Test
        fun `Returns the correct number when count function is called`() {
            val bot = bots.bot("Join").join()
            val channel = "#channel"
            val regex = "@[0-9a-z]*"
            val user = courseApp.login("user", "1234").join()

            bot.join(channel)
                    .thenCompose { bot.beginCount(channel, regex, MediaType.TEXT) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "@hello10".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.LOCATION, "@hello10".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "other message".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "@12world21".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .join()

            assertThat(bot.count(channel, regex, MediaType.TEXT).join(), equalTo(2L))
        }

        @Test
        fun `counts for every channel if channel parameter is null`() {
            val bot = bots.bot("Join").join()
            val channel1 = "#channel1"
            val channel2 = "#channel2"
            val message1 = "test"

            val user1 = courseApp.login("user1", "1234").join()
            val user2 = courseApp.login("user2", "1234").join()

            bot.join(channel1)
                    .thenCompose { bot.join(channel2) }
                    .thenCompose { courseApp.channelJoin(user1, channel1) }
                    .thenCompose { courseApp.channelJoin(user2, channel2) }
                    .thenCompose { bot.beginCount(null, message1) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user1, channel1, msg) }
                    .thenCompose { messageFactory.create(MediaType.LOCATION, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user2, channel2, msg) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "other message".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user1, channel1, msg) }
                    .join()

            assertThat(bot.count(regex = message1).join(), equalTo(2L))
        }

        @Test
        fun `regex or mediaType as wildcards works properly`() {
            val bot = bots.bot("Join").join()
            val channel = "#channel"
            val message1 = "test"
            val message2 = "other test"

            val user = courseApp.login("user", "1234").join()

            bot.join(channel)
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { bot.beginCount(channel, message1) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.LOCATION, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "other message".toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .join()

            assertThat(bot.count(channel, message1).join(), equalTo(2L))

            bot.beginCount(channel, mediaType = MediaType.TEXT).join()

            assertThat(bot.count(channel, mediaType = MediaType.TEXT).join(), equalTo(0L))

            messageFactory.create(MediaType.TEXT, message1.toByteArray())
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.LOCATION, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.AUDIO, message1.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, message2.toByteArray()) }
                    .thenCompose { msg -> courseApp.channelSend(user, channel, msg) }

            assertThat(bot.count(channel, mediaType = MediaType.TEXT).join(), equalTo(2L))
        }
    }

    @Nested
    inner class CalculationTests {

        @Test
        fun `A calculation trigger successfully evaluates an arithmetic expression`() {
            val bot = bots.bot("MathProfessor").join()
            val channel = "#math"

            val user = courseApp.login("user", "1234").join()

            val callback: ListenerCallback = mockk(relaxed = true)
            val msg = slot<Message>()
            every {
                callback("#math@MathProfessor", capture(msg))
            } answers {
                CompletableFuture.completedFuture(Unit)
            }

            courseApp.addListener(user, callback)

            bot.setCalculationTrigger("calculate")
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "calculate (    2 * (8- 4) / 2 - 2 / 1)".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .join()


            verify { callback(match { it == "#math@MathProfessor" }, any()) }
            assertThat(String(msg.captured.contents), equalTo("2"))

        }

        @Test
        fun `A calculation trigger is overridden when set for a second time`() {
            val bot = bots.bot("MathProfessor").join()
            val channel = "#math"

            val user = courseApp.login("user", "1234").join()

            val callback: ListenerCallback = mockk(relaxed = true)
            val msg = slot<Message>()
            every {
                callback("#math@MathProfessor", capture(msg))
            } answers {
                CompletableFuture.completedFuture(Unit)
            }

            courseApp.addListener(user, callback)

            var previousPhrase: String? = null

            bot.setCalculationTrigger("calculate")
                    .thenCompose { bot.setCalculationTrigger("evaluate").thenApply { previousPhrase = it } }
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "evaluate 3 + 4".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "calculate (    2 * (8- 4) / 2 - 2 / 1)".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .join()


            assertThat(previousPhrase, equalTo("calculate"))
            verify { callback(match { it == "#math@MathProfessor" }, any()) }
            assertThat(String(msg.captured.contents), equalTo("7"))
        }

        @Test
        fun `Calculator mode is disabled when called with null trigger`() {
            val bot = bots.bot("MathProfessor").join()
            val channel = "#math"

            val user = courseApp.login("user", "1234").join()

            val callback: ListenerCallback = mockk(relaxed = true)
            val msg = slot<Message>()
            every {
                callback("#math@MathProfessor", capture(msg))
            } answers {
                CompletableFuture.completedFuture(Unit)
            }

            courseApp.addListener(user, callback)

            var previousPhrase: String? = null

            bot.setCalculationTrigger("calculate")
                    .thenCompose { bot.setCalculationTrigger(null).thenApply { previousPhrase = it } }
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "calculate (    2 * (8- 4) / 2 - 2 / 1)".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .join()

            assertThat(previousPhrase, equalTo("calculate"))
            verify { callback(match { it == "#math@MathProfessor" }, any()) wasNot Called }
            assertThat(msg.isCaptured, equalTo(false))
        }
    }

    @Nested
    inner class TipTests {

        @Test
        fun `tip system is working properly and richestUse is properly updated`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#shop"

            val user = courseApp.login("user", "1234").join()
            val otherUser = courseApp.login("otherUser", "1234").join()

            bot.setTipTrigger("tip")
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { courseApp.channelJoin(otherUser, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 1 user".toByteArray()) }
                    // user = 1001
                    // otherUser = 999
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 500 otherUser".toByteArray()) }
                    // user = 501
                    // otherUser = 1499
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .join()

            assertThat(bot.richestUser(channel).join(), equalTo("otherUser"))

            messageFactory.create(MediaType.TEXT, "tip 1500 user".toByteArray())
                    // user = 501
                    // otherUser = 1499
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .join()

            assertThat(bot.richestUser(channel).join(), equalTo("otherUser"))

            messageFactory.create(MediaType.TEXT, "tip 1499 nonExistingUser".toByteArray())
                    // user = 501
                    // otherUser = 1499
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .join()

            assertThat(bot.richestUser(channel).join(), equalTo("otherUser"))

            messageFactory.create(MediaType.TEXT, "tip 1499 user".toByteArray())
                    // user = 501
                    // otherUser = 1499
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .join()

            assertThat(bot.richestUser(channel).join(), equalTo("user"))
        }

        @Test
        fun `throws NoSuchEntityException if the bot is not in the provided channel`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#shop"

            val user = courseApp.login("user", "1234").join()
            val otherUser = courseApp.login("otherUser", "1234").join()

            bot.setTipTrigger("tip")
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { courseApp.channelJoin(otherUser, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 1 user".toByteArray()) }
                    // user = 1001
                    // otherUser = 999
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 500 otherUser".toByteArray()) }
                    // user = 501
                    // otherUser = 1499
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .thenCompose { bot.part(channel) }
                    .join()

            assertThrows<NoSuchEntityException> {
                bot.richestUser(channel).joinException()
            }
        }

        @Test
        fun `returns null if no tipping has occured`() {
            val bot = bots.bot("Cashier").join()

            val value = bot.setTipTrigger("tip")
                    .thenCompose { bot.join("#channel") }
                    .thenCompose { bot.richestUser("#channel") }
                    .join()

            assertThat(value, equalTo<String>(null))
        }

        @Test
        fun `returns null if no one user is the richest`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#shop"

            val user = courseApp.login("user", "1234").join()
            val otherUser = courseApp.login("otherUser", "1234").join()

            bot.setTipTrigger("tip")
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.channelJoin(user, channel) }
                    .thenCompose { courseApp.channelJoin(otherUser, channel) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 1 user".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(otherUser, channel, m) }
                    .thenCompose { messageFactory.create(MediaType.TEXT, "tip 1 otherUser".toByteArray()) }
                    .thenCompose { m -> courseApp.channelSend(user, channel, m) }
                    .join()

            assertThat(bot.richestUser(channel).join(), equalTo<String>(null))
        }
    }

    @Nested
    inner class SeenTimeTests {
        @Test
        fun `returns the correct time when seenTime is called`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#talks"

            val user1 = courseApp.login("user1", "1234").join()
            val user2 = courseApp.login("user2", "1234").join()
            val user3 = courseApp.login("user3", "1234").join()

            // Ensure messages get different time stamps
            val msg1 = messageFactory.create(MediaType.TEXT, "hello!".toByteArray()).join()
            Thread.sleep(10)
            val msg2 = messageFactory.create(MediaType.TEXT, "world!".toByteArray()).join()
            Thread.sleep(10)
            val msg3 = messageFactory.create(MediaType.TEXT, "third!".toByteArray()).join()
            Thread.sleep(10)
            val msg4 = messageFactory.create(MediaType.TEXT, "foo!".toByteArray()).join()

            bot.join(channel)
                    .thenCompose { courseApp.channelJoin(user1, channel) }
                    .thenCompose { courseApp.channelJoin(user2, channel) }
                    .thenCompose { courseApp.channelJoin(user3, channel) }
                    .thenCompose { courseApp.channelSend(user1, channel, msg1) }
                    .thenCompose { courseApp.channelSend(user2, channel, msg2) }
                    .thenCompose { courseApp.channelSend(user3, channel, msg3) }
                    .thenCompose { courseApp.channelSend(user3, channel, msg4) }
                    .join()

            assertThat(bot.seenTime("user1").join()
                    , equalTo(msg1.created))
            assertThat(bot.seenTime("user2").join()
                    , equalTo(msg2.created))
            assertThat(bot.seenTime("user3").join()
                    , equalTo(msg4.created))

        }

        @Test
        fun `returns null if the user has not sent any messages or the user does not exist`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#talks"

            val user1 = courseApp.login("user1", "1234").join()
            bot.join(channel)
                    .thenCompose { courseApp.channelJoin(user1, channel) }
                    .join()

            assertThat(bot.seenTime("user1").join(), equalTo<LocalDateTime?>(null))
            assertThat(bot.seenTime("non_existing").join(), equalTo<LocalDateTime?>(null))
        }


        @Test
        fun `returns the correct user when mostActiveUser is called`() {
            val bot = bots.bot("Cashier").join()
            val channel = "#talks"

            val user1 = courseApp.login("user1", "1234").join()
            val user2 = courseApp.login("user2", "1234").join()
            val user3 = courseApp.login("user3", "1234").join()

            // Ensure messages get different time stamps
            val msg1 = messageFactory.create(MediaType.TEXT, "hello!".toByteArray()).join()
            Thread.sleep(10)
            val msg2 = messageFactory.create(MediaType.TEXT, "world!".toByteArray()).join()
            Thread.sleep(10)
            val msg3 = messageFactory.create(MediaType.TEXT, "third!".toByteArray()).join()
            Thread.sleep(10)
            val msg4 = messageFactory.create(MediaType.TEXT, "foo!".toByteArray()).join()

            bot.join(channel)
                    .thenCompose { courseApp.channelJoin(user1, channel) }
                    .thenCompose { courseApp.channelJoin(user2, channel) }
                    .thenCompose { courseApp.channelJoin(user3, channel) }
                    .thenCompose { courseApp.channelSend(user1, channel, msg1) }
                    .thenCompose { courseApp.channelSend(user2, channel, msg2) }
                    .thenCompose { courseApp.channelSend(user3, channel, msg3) }
                    .join()

            assertThat(bot.mostActiveUser(channel).join(), equalTo<String?>(null))

            courseApp.channelSend(user2, channel, msg4)

            assertThat(bot.mostActiveUser(channel).join(), equalTo("user2"))
        }
    }

    @Nested
    inner class SurveyTests {

        @Test
        fun `Throws NoSuchEntityException if the surveying bot is not a part of the channel`() {
            val bot = bots.bot("Join").join()
            val channel1 = "#channel1"

            val question = "What is the meaning of life?"
            val a1 = "42"
            val a2 = "The ultimate answer"
            val a3 = "Taub"
            val a4 = "All of the above"
            val answers = listOf(a1, a2, a3, a4)

            assertThrows<NoSuchEntityException> { bot.runSurvey(channel1, question, answers).joinException() }
        }

        @Test
        fun `Throws NoSuchEntityException if a survey does not exist`() {
            val bot = bots.bot("John").join()
            assertThrows<NoSuchEntityException> { bot.surveyResults("invalid_survey").joinException() }
        }

        private fun runTestSurvey(): Triple<CourseBot, String, List<Long>> {
            val bot = bots.bot("John").join()
            val channel1 = "#facebook"

            val user1 = courseApp.login("user1", "1234").join()
            val user2 = courseApp.login("user2", "1234").join()

            val question = "What is the meaning of life?"
            val a1 = "42"
            val a2 = "The ultimate answer"
            val a3 = "Taub"
            val a4 = "All of the above"
            val answers = listOf(a1, a2, a3, a4)

            val callback: ListenerCallback = mockk(relaxed = true)
            val msg = slot<Message>()
            every {
                callback("#facebook@John", capture(msg))
            } answers {
                CompletableFuture.completedFuture(Unit)
            }

            val survey = bot.join(channel1)
                    .thenCompose { bot.runSurvey(channel1, question, answers) }
                    .thenForward { courseApp.channelJoin(user1, channel1) }
                    .thenForward { courseApp.channelJoin(user2, channel1) }
                    .thenForward { courseApp.addListener(user1, callback) }
                    .thenForward { courseApp.addListener(user2, callback) }
                    .join()

            verify(exactly = 3) { callback(match { it == "#facebook@John" }, any()) }
            assertThat(String(msg.captured.contents), equalTo(question))

            val results = courseApp.channelSend(user1, channel1, messageFactory.create(MediaType.TEXT, a1.toByteArray()).join())
                    .thenCompose { courseApp.channelSend(user2, channel1, messageFactory.create(MediaType.TEXT, a2.toByteArray()).join()) }
                    .thenCompose { courseApp.channelSend(user2, channel1, messageFactory.create(MediaType.TEXT, a4.toByteArray()).join()) }
                    .thenCompose { bot.surveyResults(survey) }
                    .join()

            return Triple(bot, survey, results)
        }

        @Test
        fun `successfully starts a survey and returns the results`() {
            val (_, _, results) = runTestSurvey()
            assertThat(results, equalTo(listOf(1L, 0L, 0L, 1L)))
        }

        @Test
        fun `if a bot leaves a channel the survey stays but the results are reset`() {
            val (bot, survey, results) = runTestSurvey()
            assertThat(results, equalTo(listOf(1L, 0L, 0L, 1L)))

            val newResults = bot.part("#facebook")
                    .thenCompose { bot.surveyResults(survey) }
                    .join()

            assertThat(newResults, equalTo(listOf(0L, 0L, 0L, 0L)))
        }
    }

    @Test
    fun `if a bot leaves a channel the tipping for that channel is reset`() {

    }

    @Test
    fun `A bot channel-specific statistics are not shared between bots`() {

    }
}