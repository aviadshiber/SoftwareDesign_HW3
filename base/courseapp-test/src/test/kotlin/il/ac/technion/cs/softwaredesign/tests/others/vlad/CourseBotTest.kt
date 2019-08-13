package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.CourseBot
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.tests.containsElementsInOrder
import il.ac.technion.cs.softwaredesign.tests.joinException
import il.ac.technion.cs.softwaredesign.tests.runWithTimeout
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger


@TestInstance(TestInstance.Lifecycle.PER_METHOD)
open class CourseBotTest : CourseTest() {
    protected open val adminToken: String = courseApp.login("admin", "password").join()
    protected open var bot: CourseBot = bots.bot().join()

    @Nested
    open inner class JoinPartTest {
        @Test
        fun `UserNotAuthorizedException is thrown when join not existing channel`() {
            assertThrows<UserNotAuthorizedException> { bot.join("#archenemy").joinException() }
        }

        @Test
        fun `UserNotAuthorizedException is thrown when join channel with bad name`() {
            assertThrows<UserNotAuthorizedException> { bot.join("archenemy").joinException() }
        }

        @Test
        fun `UserNotAuthorizedException is thrown when part from not existing channel`() {
            assertThrows<UserNotAuthorizedException> { bot.join("#archenemy").joinException() }
        }

        @Test
        fun `NoSuchEntityException is thrown when part from not related channel`() {
            courseApp.channelJoin(adminToken, "#archenemy").join()
            reboot()
            assertThrows<NoSuchEntityException> { bot.part("#archenemy").joinException() }
        }

        @Test
        fun `adding to channel when joining channel`() {
            val channel = "#channel"
            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            assertThat(bot.channels().join(), containsElementsInOrder(channel))
        }

        @Test
        fun `removing from channel when parting channel`() {
            val channel = "#channel"
            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.part(channel) }
                    .join()

            assertThat(bot.channels().join(), containsElementsInOrder())
        }
    }

    @Nested
    open inner class CountTest {
        @Test
        fun `IllegalArgumentException is thrown when both MediaType and RegEx are null`() {
            assertThrows<IllegalArgumentException> { bot.beginCount(null, mediaType = null).joinException() }
        }

        @Test
        fun `IllegalArgumentException thrown when count on new regexp media pair`() {
            assertThrows<IllegalArgumentException> { bot.count(null, regex = "I.*").joinException() }
        }

        @Test
        fun `bot counts message with any media type and in any channel`() {
            channelWithAudioAndChannelWithText()

            assertThat(bot.count(regex = "I.*").join(), equalTo(2L))
        }

        @Test
        fun `bot counts message in any channel which contains single such message`() {
            channelWithAudioAndChannelWithText(channel = "#kiss")

            assertThat(bot.count(channel = "#kiss", regex = "I.*").join(), equalTo(1L))
        }

        @Test
        fun `bot counts message with text in any channel which contains no such message`() {
            channelWithAudioAndChannelWithText(channel = "#rammstein")

            assertThat(bot.count(channel = "#rammstein", regex = "I.*").join(), equalTo(0L))
        }

        @Test
        fun `IllegalArgumentException after parting from channel count`() {
            val channel = "#hell"
            val regex = ".*"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(channel = channel, regex = regex) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42") }
                    .thenCompose { bot.part(channel) }
                    .join()

            assertThat(bot.count(channel, regex).join(), present(equalTo(0L)))
        }

        @Test
        fun `IllegalArgumentException after parting and joining from count`() {
            val channel = "#hell"
            val regex = ".*"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(channel = channel, regex = regex) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42") }
                    .thenCompose { bot.part(channel) }
                    .thenCompose { bot.join(channel) }
                    .join()
            assertThat(bot.count(channel, regex).join(), present(equalTo(0L)))
        }

        @Test
        fun `0 after parting and joining and begining again from count`() {
            val channel = "#hell"
            val regex = ".*"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(channel = channel, regex = regex) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42") }
                    .thenCompose { bot.part(channel) }
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(channel = channel, regex = regex) }
                    .join()

            assertThat(bot.count(channel, regex).joinException(), equalTo(0L))
        }

        @Test
        fun `bot does not count private messages`() {
            val channel = "#"
            val regex = ".*"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(regex = regex) }
                    .thenApply { sendMessageToUser(adminToken, "Anna0", MediaType.TEXT, "23") }
                    .join()

            assertThat(bot.count(regex = regex).join(), equalTo(0L))
        }

        @Test
        fun `bot does not count broadcast messages`() {
            val channel = "#"
            val regex = ".*"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.beginCount(regex = regex) }
                    .thenApply { sendMessageBroadcast(adminToken, MediaType.TEXT, "23") }
                    .join()

            assertThat(bot.count(regex = regex).join(), equalTo(0L))
        }

        private fun channelWithAudioAndChannelWithText(channel: String? = null) {
            val channel1 = "#kiss"
            val channel2 = "#AE"
            val channel3 = "#rammstein"
            val regex = "I.*"

            courseApp
                    .channelJoin(adminToken, channel1)
                    .thenCompose { courseApp.channelJoin(adminToken, channel2) }
                    .thenCompose { courseApp.channelJoin(adminToken, channel3) }
                    .thenCompose { bot.join(channel1) }
                    .thenCompose { bot.join(channel2) }
                    .thenCompose { bot.join(channel3) }
                    .thenCompose { bot.beginCount(channel = channel, regex = regex) }
                    .thenApply {
                        sendMessageToChannel(adminToken, channel1, MediaType.TEXT, "I was made for loving you")
                        sendMessageToChannel(adminToken, channel2, MediaType.AUDIO, "I was made for loving you")
                        sendMessageToChannel(adminToken, channel3, MediaType.LOCATION, "ParIs")
                    }
                    .join()
        }
    }

    @Nested
    open inner class TipTest {
        @Test
        fun `richest user before actions is null`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            reboot()

            assertThat(bot.richestUser(channel).join(), absent())
        }

        @Test
        fun `NoSuchEntityException thrown if bot not in channel`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .join()

            reboot()

            assertThrows<NoSuchEntityException> { bot.richestUser(channel).joinException() }
        }

        @Test
        fun `tip for user in same channel makes user richest`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.login("user", "pass") }
                    .thenCompose { token ->
                        courseApp
                                .channelJoin(token, channel)
                                .thenCompose { bot.setTipTrigger("tip") }
                                .thenApply { sendMessageToChannel(token, channel, MediaType.TEXT, "tip 10 admin") }
                    }
                    .join()

            reboot()

            assertThat(bot.richestUser(channel).join(), equalTo("admin"))
        }

        @Test
        fun `setTipTrigger returns null if no trigger set`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            reboot()

            assertThat(bot.setTipTrigger("trigger").join(), absent())
        }

        @Test
        fun `setTipTrigger returns previous tip if invoked several times`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.setTipTrigger("trigger") }
                    .join()
            reboot()

            assertThat(bot.setTipTrigger("tip").join(), equalTo<String>("trigger"))
        }

        @Test
        fun `setTipTrigger independent for different bots`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bots.bot() }
                    .thenCompose { bot2 ->
                        bot2.join(channel)
                                .thenCompose { bot2.setTipTrigger("trigger") }
                    }
                    .join()
            reboot()

            assertThat(bot.setTipTrigger("trigger").join(), absent())
        }

//        @Test
//        fun `tip and leave makes another user richest`() {
//            val channel = "#disturbed"
//
//            courseApp
//                    .channelJoin(adminToken, channel)
//                    .thenCompose { bot.join(channel) }
//                    .thenCompose { courseApp.login("user", "pass") }
//                    .thenCompose { token ->
//                        courseApp
//                                .channelJoin(token, channel)
//                                .thenCompose { bot.setTipTrigger("tip") }
//                                .thenApply { sendMessageToChannel(token, channel, MediaType.TEXT, "tip 10 admin") }
//                                .thenCompose { courseApp.channelPart(adminToken, channel) }
//                    }
//                    .join()
//
//            reboot()
//
//            assertThat(bot.richestUser(channel).join(), equalTo("user"))
//        }

        @Test
        fun `A user in the channel can tip another user`() {
            CompletableFuture.completedFuture(adminToken)
                    .thenCompose { adminToken ->
                        courseApp.channelJoin(adminToken, "#channel")
                                .thenCompose {
                                    bots.bot("Anna0")
                                            .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                            .thenCompose { bot -> bot.setTipTrigger("tip") }
                                }
                                .thenCompose { courseApp.login("matan", "s3kr3t") }
                                .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                                .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 admin".toByteArray()).join()) }
                    }.join()

            reboot()

            assertThat(runWithTimeout(Duration.ofSeconds(10)) {
                bots.bot("Anna0")
                        .thenCompose { it.richestUser("#channel") }
                        .join()
            }, present(equalTo("admin")))
        }

        @Test
        fun `tipping when has not enough bits does nothing`() {
            val token = courseApp.login("user", "password").join()
            val token1 = courseApp.login("user1", "password").join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token, token1),
                    listOf(
                            Triple(adminToken, 100L, "user"),
                            Triple(adminToken, 901L, "user1")
                    )
            ), present(equalTo("user")))
        }

        @Test
        fun `tipping when just enough bits does the thing`() {
            val token = courseApp.login("user", "password").join()
            val token1 = courseApp.login("user1", "password").join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token, token1),
                    listOf(
                            Triple(adminToken, 100L, "user"),
                            Triple(adminToken, 900L, "user1")
                    )
            ), present(equalTo("user1")))
        }

        @Test
        fun `tipping not existing user is not possible`() {
            val token = courseApp.login("user", "password").join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token),
                    listOf(
                            Triple(adminToken, 100L, "user"),
                            Triple(token, 101L, "notExistingUser")
                    )
            ), present(equalTo("user")))
        }

        @Test
        fun `tipping via different bots is independent in same channel `() {
            val token1 = courseApp.login("user1", "pass1").join()
            val token2 = courseApp.login("user2", "pass2").join()
            val bot1 = bots.bot().join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1, token2),
                    listOf(Triple(token1, 1000L, "user1"))
            ), equalTo("user1"))
            bot.setTipTrigger(null).join()
            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1, token2),
                    listOf(Triple(token1, 500L, "user2")),
                    botName = "Anna1"
            ), equalTo("user2"))

            assertThat(bot.richestUser("#disturbed").join(), equalTo("user1"))
        }

        @Test
        fun `tipping via different channels is independent via same bot`() {
            val token1 = courseApp.login("user1", "pass1").join()
            val token2 = courseApp.login("user2", "pass2").join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1, token2),
                    listOf(Triple(token1, 1000L, "user1")),
                    channel = "#42"
            ), equalTo("user1"))
            bot.setTipTrigger(null).join()
            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1, token2),
                    listOf(Triple(token1, 500L, "user2")),
                    channel = "#23"
            ), equalTo("user2"))

            assertThat(bot.richestUser("#42").join(), equalTo("user1"))
        }

        @Test
        fun `if user not in channel tipping does nothing`() {
            val token1 = courseApp.login("user1", "pass1").join()
            val token2 = courseApp.login("user2", "pass2").join()

            assertThat(createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1),
                    listOf(
                            Triple(adminToken, 200L, "user1"),
                            Triple(token1, 1200L, "user2")
                    )
            ), equalTo("user1"))
        }

        @Test
        fun `after user part he is still richest`() {
            val channel = "#taub"
            val token1 = courseApp.login("user", "pass").join()

            createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1),
                    listOf(Triple(adminToken, 42L, "user")),
                    channel = channel
            )

            courseApp.channelPart(token1, channel).join()

            reboot()

            assertThat(bot.richestUser(channel).join(), present(equalTo("user")))
        }

        @Test
        fun `tipping does not occur via private message`() {
            val channel = "#"
            val trigger = "tip"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.setTipTrigger(trigger) }
                    .thenApply { sendMessageToUser(adminToken, "Anna0", MediaType.TEXT, "tip 10 Anna0") }
                    .join()

            assertThat(bot.richestUser(channel).join(), absent())
        }

        @Test
        fun `tipping does not occur via broadcast`() {
            val channel = "#"
            val trigger = "tip"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.setTipTrigger(trigger) }
                    .thenApply { sendMessageBroadcast(adminToken, MediaType.TEXT, "tip 10 Anna0") }
                    .join()

            assertThat(bot.richestUser(channel).join(), absent())
        }

        @Test
        fun `after reconnect richest user is null`() {
            val channel = "#taub"
            val token1 = courseApp.login("user", "pass").join()

            createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1),
                    listOf(Triple(adminToken, 42L, "user")),
                    channel = channel
            )

            reconnect(bot, channel)

            reboot()

            assertThat(bot.richestUser(channel).join(), absent())
        }

        @Test
        fun `after reconnect credict is restored`() {
            val channel = "#taub"
            val token1 = courseApp.login("user", "pass").join()
            val token2 = courseApp.login("user1", "pass").join()

            createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token1),
                    listOf(Triple(adminToken, 1000L, "user")),
                    channel = channel
            )

            reconnect(bot, channel)

            createChannelWithUsersDoTippingAndReturnRichest(
                    listOf(adminToken, token2),
                    listOf(Triple(adminToken, 1000L, "user1")),
                    channel = channel
            )

            reboot()

            assertThat(bot.richestUser(channel).join(), present(equalTo("user1")))
        }

        private fun createChannelWithUsersDoTippingAndReturnRichest(
                tokens: List<String>,
                tokenTipUserName: List<Triple<String, Long, String>>,
                botName: String = "Anna0",
                trigger: String = "tip",
                channel: String = "#disturbed"
        ): String? {
            bots.bot(botName).join().setTipTrigger(trigger).join()
            tokens.forEach { courseApp.channelJoin(it, channel).join() }
            bots.bot(botName).join().join(channel).join()

            tokenTipUserName.forEach { (token, tip, userName) ->
                tip(token, tip, userName, channel, trigger)
            }

            reboot()

            return bots.bot(botName).join().richestUser(channel).join()
        }

        protected fun tip(token: String, tip: Long, userName: String, channel: String, trigger: String) {
            sendMessageToChannel(token, channel, MediaType.TEXT, "%s %d %s".format(trigger, tip, userName))
        }
    }

    // TODO: richest user with same number of usages

    @Nested
    open inner class CalculationTest {
        @Test
        fun `2+2*2==6`() {
            evaluateExpressionAndCompareResult("2 + 2 * 2", "6")
        }

        @Test
        fun `(2+2)*2==8`() {
            evaluateExpressionAndCompareResult("(2+2)*2", "8")
        }

        @Test
        fun `0div1+2*3+4-5+6-7+8-9+10==13`() {
            evaluateExpressionAndCompareResult("0/1+2*3+4-5+6-7+8-9+10", "13")
        }

        @Test
        fun `calculation and tipping work with same trigger`() {
            val channel = "#disturbed"
            val trigger = "trigger"
            val listener = createListener()

            courseApp.channelJoin(adminToken, channel).join()
            courseApp.addListener(adminToken, listener).join()
            bot.join(channel).join()
            val token = loginAndJoin("+20", channel)

            bot.setTipTrigger(trigger).join()
            bot.setCalculationTrigger(trigger).join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "trigger 1 +20")

            verify {
                listener("#disturbed@admin", any())
                listener("#disturbed@Anna0", match { it.contents contentEquals "21".toByteArray() })
            }

            confirmVerified()

            assertThat(bot.richestUser(channel).join(), equalTo("+20"))
        }

        @Test
        fun `calculateTrigger returns null on first invocation`() {
            assertThat(bot.setCalculationTrigger("calculation").join(), absent())
        }

        @Test
        fun `calculateTrigger returns previous trigger on invocation`() {
            bot.setCalculationTrigger("calculation").join()
            reboot()
            assertThat(bot.setCalculationTrigger("trigger").join(), equalTo<String>("calculation"))
        }

        @Test
        fun `calculation of wrongly formatted expressions does nothing`() {
            val channel = "#sd"
            val listener = createListener()

            bot.setCalculationTrigger("calculate").join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "calculate )(") }
                    .join()

            verify {
                listener("#sd@admin", any())
            }
            //confirmVerified(listener)
        }

        //        @Test
        fun `calculator does integer division`() {
            evaluateExpressionAndCompareResult("1/2", "0")
        }

        //        @Test
        fun `1div0 does nothing`() {
            val channel = "#sd"
            val listener = createListener()

            bot.setCalculationTrigger("calculate").join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "calculate 1/0") }
                    .join()

            verify {
                listener("#sd@admin", any())
            }
            confirmVerified(listener)
        }

        @Test
        fun `calculation does not occur via private message`() {
            val channel = "#"
            val listener = createListener()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenCompose { bot.join(channel) }
                    .thenApply { sendMessageToUser(adminToken, "Anna0", MediaType.TEXT, "calc 2+2") }
                    .join()

            //confirmVerified(listener)
        }

        @Test
        fun `calculation does not occur via broadcast`() {
            val channel = "#"
            val trigger = "calc"
            val listener = createListener()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenCompose { bot.join(channel) }
                    .join()

            val message = sendMessageBroadcast(adminToken, MediaType.TEXT, "calc 2+2")

            verify { listener("broadcast", message) }
            //confirmVerified(listener)
        }

        private fun evaluateExpressionAndCompareResult(expression: String, result: String) {
            Logger.getGlobal().fine { "evaluateExpressionAndCompareResult(%s)==%s".format(expression, result) }
            val channel = "#disturbed"
            val listener = createListener()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenCompose { bot.join(channel) }
                    .thenCompose { bot.setCalculationTrigger("calculate") }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "calculate %s".format(expression))

            verify {
                listener("#disturbed@admin", any())
                listener("#disturbed@Anna0", match { it.contents contentEquals result.toByteArray() })
            }
            //confirmVerified(listener)
        }
    }

    private fun createListener(): ListenerCallback {
        val listener = mockk<ListenerCallback>()

        every { listener(any(), any()) }.returns(CompletableFuture.completedFuture(Unit))
        return listener
    }

    @Nested
    open inner class SeenTimeTest {
        @Test
        fun `last send time before all is null`() {
            assertThat(bot.seenTime("admin").join(), equalTo<LocalDateTime>(null))
        }

        @Test
        fun `last after one message is creation time of that message`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            val message = sendMessageToChannel(adminToken, channel, MediaType.TEXT, "Land of confusion")

            reboot()

            assertThat(bot.seenTime("admin").join(), equalTo(message.created))
        }

        @Test
        fun `last after two messages is creation time of last sent message`() {
            Logger.getGlobal().fine("last after two messages is creation time of last sent message")

            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "Stupefy")
            Thread.sleep(1)
            val message = sendMessageToChannel(adminToken, channel, MediaType.TEXT, "Land of confusion")

            reboot()

            assertThat(bot.seenTime("admin").join(), equalTo(message.created))
        }
        // TODO: test not text does not trigger

        @Test
        fun `seen message is dependent on bot`() {
            val channel = "#disturbed"
            val bot1 = bots.bot().join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.AUDIO, "Stupefy")

            bot1.join(channel).join()

            reboot()

            assertThat(bot1.seenTime("admin").join(), absent())
        }

        @Test
        fun `seen message does not depend on private message`() {
            val channel = "#"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenApply { sendMessageToUser(adminToken, "Anna0", MediaType.TEXT, "calc 2+2") }
                    .join()

            assertThat(bot.seenTime(adminToken).join(), absent())
        }

        @Test
        fun `calculation does not occur via broadcast`() {
            val channel = "#"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageBroadcast(adminToken, MediaType.TEXT, "calc 2+2")

            assertThat(bot.seenTime(adminToken).join(), absent())
        }

        @Test
        fun `last seen time is same after bot part`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            val message = sendMessageToChannel(adminToken, channel, MediaType.TEXT, "Stupefy")

            bot.part(channel).join()

            reboot()

            assertThat(bot.seenTime("admin").join(), equalTo(message.created))
        }

        @Test
        fun `last seen time is same after bot reconnect`() {
            val channel = "#disturbed"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            val message = sendMessageToChannel(adminToken, channel, MediaType.TEXT, "Stupefy")

            reconnect(bot, channel)

            reboot()

            assertThat(bot.seenTime("admin").join(), equalTo(message.created))
        }
    }

    @Nested
    open inner class ActiveTest {
        @Test
        fun `NoSuchEntityException is thrown when channel does not exist`() {
            assertThrows<NoSuchEntityException> { bot.mostActiveUser("#disturbed").joinException() }
        }

        @Test
        fun `NoSuchEntityException is thrown when bot not joined`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel).join()
            reboot()

            assertThrows<NoSuchEntityException> { bot.mostActiveUser(channel).joinException() }
        }

        @Test
        fun `NoSuchEntityException is thrown when bot parted`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42") }
                    .thenCompose { bot.part(channel) }
                    .join()
            reboot()

            assertThrows<NoSuchEntityException> { bot.mostActiveUser(channel).joinException() }
        }

        @Test
        fun `null is returned when seen no activity`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            reboot()

            assertThat(bot.mostActiveUser(channel).join(), absent())
        }

        @Test
        fun `after one text message this user is most active`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "23")
            reboot()

            assertThat(bot.mostActiveUser(channel).join(), equalTo("admin"))

        }

        @Test
        fun `after two messages another user is most active`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            val token = courseApp.login("user", "password")
                    .whenComplete { token, _ -> courseApp.channelJoin(token, channel).join() }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "23")
            sendMessageToChannel(token, channel, MediaType.TEXT, "technion")
            sendMessageToChannel(token, channel, MediaType.TEXT, "taub")
            reboot()

            assertThat(bot.mostActiveUser(channel).join(), equalTo("user"))
        }

        @Test
        fun `several users have same number of messages returns null`() {
            val channel = "#taub"
            val token1 = courseApp.login("user", "pass").join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.channelJoin(token1, channel) }
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(token1, channel, MediaType.TEXT, "42")
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "23")

            assertThat(bot.mostActiveUser(channel).join(), absent())
        }

        @Test
        fun `FILE ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.FILE)
        }

        @Test
        fun `PICTURE ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.PICTURE)
        }

        @Test
        fun `STICKER ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.STICKER)
        }

        @Test
        fun `AUDIO ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.AUDIO)
        }

        @Test
        fun `LOCATION ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.LOCATION)
        }

        @Test
        fun `REFERENCE ignored when computing most active user`() {
            messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(MediaType.REFERENCE)
        }

        private fun messagesWithMediaTypeIgnoredWhenComputingMostActiveUser(mediaType: MediaType) {
            val channel = "#taub"
            val token1 = courseApp.login("user", "pass").join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.channelJoin(token1, channel) }
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(token1, channel, MediaType.TEXT, "23")
            sendMessageToChannel(adminToken, channel, mediaType, "42")
            sendMessageToChannel(adminToken, channel, mediaType, "42")

            assertThat(bot.mostActiveUser(channel).join(), equalTo("user"))
        }

        @Test
        fun `mostActiveUser is bot dependent`() {
            val channel = "#taub"
            val token1 = courseApp.login("user1", "pass").join()

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { courseApp.channelJoin(token1, channel) }
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")

            val bot1 = bots.bot().join()
            bot1.join(channel).join()

            reboot()

            assertThat(bot1.mostActiveUser(channel).join(), absent())
        }

        @Test
        fun `mostActiveUser after user is parted stays the same`() {
            val channel = "#taub"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")
            courseApp.channelPart(adminToken, channel).join()

            reboot()

            assertThat(bot.mostActiveUser(channel).join(), present(equalTo("admin")))
        }

        @Test
        fun `mostActiveUser does not depend on private message`() {
            val channel = "#"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenApply { sendMessageToUser(adminToken, "Anna0", MediaType.TEXT, "calc 2+2") }
                    .join()

            assertThat(bot.mostActiveUser(channel).join(), absent())
        }

        @Test
        fun `mostActiveUser does not occur via broadcast`() {
            val channel = "#"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            sendMessageBroadcast(adminToken, MediaType.TEXT, "calc 2+2")

            assertThat(bot.mostActiveUser(channel).join(), absent())
        }

        @Test
        fun `mostActiveUser throws NoSuchEntityException after bot parted`() {
            val channel = "#taub"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")

            bot.part(channel).join()

            reboot();

            assertThrows<NoSuchEntityException> { bot.mostActiveUser(channel).joinException() }
        }

        @Test
        fun `mostActiveUser returns null after bot rejointed`() {
            val channel = "#taub"

            courseApp
                    .channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")

            reconnect(bot, channel) // part channel + join channel

            //reboot()

            assertThat(bot.mostActiveUser(channel).join(), absent())
        }
    }

    @Nested
    open inner class SurveyTest {
        @Test
        fun `NoSuchEntityException is thrown when channel does not exist`() {
            assertThrows<NoSuchEntityException> { bot.runSurvey("#disturbed", "?", listOf("")).joinException() }
        }

        @Test
        fun `NoSuchEntityException is thrown when bot not joined`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel).join()
            reboot()

            assertThrows<NoSuchEntityException> { bot.runSurvey("#disturbed", "?", listOf("")).joinException() }
        }

        @Test
        fun `NoSuchEntityException is thrown when bot parted`() {
            val channel = "#disturbed"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenApply { sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42") }
                    .thenCompose { bot.part(channel) }
                    .join()
            reboot()

            assertThrows<NoSuchEntityException> { bot.runSurvey("#disturbed", "?", listOf("")).joinException() }
        }

        @Test
        fun `running survey sends question`() {
            val channel = "#hamlet"
            val question = "to be or not to be"
            val listener = createListener()

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose { courseApp.addListener(adminToken, listener) }
                    .thenCompose { bot.runSurvey(channel, question, listOf()) }
                    .join()

            verify {
                listener("#hamlet@Anna0", match {
                    it.contents contentEquals question.toByteArray()
                            && it.media == MediaType.TEXT
                })
            }
            //confirmVerified(listener)
        }

        @Test
        fun `answering survey gives correct result`() {
            val channel = "#hamlet"
            val question = "to be or not to be"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            reboot()

            assertThat(
                    startSurveyAndVote(
                            channel,
                            question,
                            listOf("2b", "!2b"),
                            listOf(
                                    Pair(adminToken, "2b")
                            )
                    ),
                    equalTo(listOf(1L, 0L))
            )
        }

        @Test
        fun `answering survey twice with same vote counts once`() {
            val channel = "#hamlet"
            val question = "to be or not to be"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            reboot()

            assertThat(
                    startSurveyAndVote(
                            channel,
                            question,
                            listOf("2b", "!2b"),
                            listOf(
                                    Pair(adminToken, "2b"),
                                    Pair(adminToken, "2b")
                            )
                    ),
                    equalTo(listOf(1L, 0L))
            )
        }

        @Test
        fun `answering survey twice with different votes changes vote`() {
            val channel = "#hamlet"
            val question = "to be or not to be"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()
            reboot()

            assertThat(
                    startSurveyAndVote(
                            channel,
                            question,
                            listOf("2b", "!2b"),
                            listOf(
                                    Pair(adminToken, "2b"),
                                    Pair(adminToken, "!2b")
                            )
                    ),
                    equalTo(listOf(0L, 1L))
            )
        }

        @Test
        fun `answering survey by different users is ok`() {
            val channel = "#disturbed"
            val question = "are you ready?"

            courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .join()

            val token1 = loginAndJoin("user1", channel)
            val token2 = loginAndJoin("user2", channel)

            assertThat(
                    startSurveyAndVote(
                            channel,
                            question,
                            listOf("yes", "no", "get up"),
                            listOf(
                                    Pair(adminToken, "yes"),
                                    Pair(token1, "get up"),
                                    Pair(token2, "get up")
                            )
                    ),
                    equalTo(listOf(1L, 0L, 2L))
            )
        }

        @Test
        fun `NoSuchEntityException when surveyResults with wrong identifier`() {
            assertThrows<NoSuchEntityException> { bot.surveyResults("identifier").joinException() }
        }

        @Test
        fun `can vote after survey results`() {
            val channel = "#channel"

            val identifier = courseApp.channelJoin(adminToken, channel)
                    .thenCompose { bot.join(channel) }
                    .thenCompose {
                        bot.runSurvey(
                                channel,
                                "2b||~2b",
                                listOf("2b", "~2b"))
                    }
                    .join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "2b")

            bot.surveyResults(identifier).join()

            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "~2b")

            assertThat(
                    bot.surveyResults(identifier).join(),
                    containsElementsInOrder(0L, 1L)
            )
        }

        private fun startSurveyAndVote(
                channel: String,
                question: String,
                answers: List<String>,
                tokenVote: List<Pair<String, String>>
        ): List<Long> {
            tokenVote.forEach { (token, _) -> courseApp.channelJoin(token, channel).join() }
            val identifier = bot.runSurvey(channel, question, answers).join()
            tokenVote.forEach { (token, vote) -> sendMessageToChannel(token, channel, MediaType.TEXT, vote) }
            reboot()

            return bot.surveyResults(identifier).join()
        }

    }

    // TODO: TEST leaving the channel resets all channel-specific statistics for that particular channel.

    protected fun sendMessageToChannel(token: String, channel: String, mediaType: MediaType, contents: String): Message {
        val message = messageFactory.create(mediaType, contents.toByteArray()).join()
        courseApp.channelSend(token, channel, message).join()

        return message
    }

    private fun sendMessageToUser(token: String, username: String, mediaType: MediaType, contents: String): Message {
        val message = messageFactory.create(mediaType, contents.toByteArray()).join()
        courseApp.privateSend(token, username, message).join()

        return message
    }

    private fun sendMessageBroadcast(token: String, mediaType: MediaType, contents: String): Message {
        val message = messageFactory.create(mediaType, contents.toByteArray()).join()
        courseApp.broadcast(token, message).join()

        return message
    }

    protected fun loginAndJoin(userName: String, channelName: String): String {
        return courseApp
                .login(userName, "password")
                .whenComplete { token, _ ->
                    courseApp.channelJoin(token, channelName).join()
                }
                .join()
    }

    private fun reconnect(bot: CourseBot, channelName: String) {
        bot.part(channelName).thenCompose { bot.join(channelName) }.join()
    }
}