package il.ac.technion.cs.softwaredesign.tests

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofSeconds
import java.util.concurrent.CompletableFuture

class CourseBotStaffTest {
    private val injector = Guice.createInjector(CourseAppModule(), CourseBotModule(), TestModule())

    init {
        injector.getInstance<CourseAppInitializer>().setup().join()
    }

    private val courseApp = injector.getInstance<CourseApp>()
    private val bots = injector.getInstance<CourseBots>()
    private val messageFactory = injector.getInstance<MessageFactory>()

    init {
        bots.prepare().join()
        bots.start().join()
    }

    @Test
    fun `bot cannot join to a channel that does not exist`() {
        courseApp.login("aviad", "hunter2").thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }.join()
        val bot = bots.bot().thenCompose { bot -> bot.join("#channel").thenApply { bot } }.join()

        assertThrows<UserNotAuthorizedException> {
            runWithTimeout(ofSeconds(10)) {
                bot.join("#notExistingChannel").joinException()
                bot.join("notExistingChannel").joinException()
            }
        }
    }

    /*@Test
    fun `bot cannot do action on a a channel after he was kicked`(){
        val channelName="#channel"
        val adminToken=courseApp.login("aviad", "hunter2").thenCompose { adminToken -> courseApp.channelJoin(adminToken, channelName).thenApply { adminToken } }.join()
        val bot=bots.bot("mybot").thenCompose { bot -> bot.join(channelName).thenApply { bot }}.join()
        courseApp.channelKick(adminToken,channelName,"mybot")
        assertThrows<UserNotAuthorizedException>{
            runWithTimeout(ofSeconds(10)) {
                bot.mostActiveUser(channelName).joinException()
                bot.richestUser(channelName).joinException()
                bot.beginCount(channelName,"",null).joinException()
                bot.count(channelName,"",null).joinException()
                bot.runSurvey(channelName,"what?!", listOf()).joinException()
            }
        }
    }*/

    @Test
    fun `bot cash tracks get reset after bot leave channel`() {
        courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot }}
                .thenCompose { bot->bot.setTipTrigger("tip").thenApply { bot }}.join()
        courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser("#channel", "tip 1000 aviad", token) }
                .join()
        courseApp.login("ron", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser("#channel", "tip 500 shahar", token) }
                .join()
        assertThat(runWithTimeout(ofSeconds(10)) {
            bot.richestUser("#channel").get()
        }, equalTo("aviad"))

        reconnectBotInChannel(bot, "#channel").join()
        val newRichest: String? = bot.richestUser("#channel").get()
        assertThat(newRichest, absent())
    }

    @Test
    fun `bot tracks for richest user but both users have same amount of cash so no such user exist`() {
        val adminToken = courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }
                .join()
        val bot = bots.bot()
                .thenCompose { bot -> bot.join("#channel").thenApply { bot }}
                .thenCompose { bot->bot.setTipTrigger("tip").thenApply { bot }}.join()
        courseApp.login("shahar", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser("#channel", "tip 1000 aviad", token) }.join()
        courseApp.login("ron", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser("#channel", "tip 1000 shahar", token) }.join()
        courseApp.login("gal", "pass")
                .thenCompose { token -> joinChannelAndSendAsUser("#channel", "tip 1000 shahar", token) }.join()
        val richest: String? = bot.richestUser("#channel").get()

        assertThat(richest, absent())
    }

    private fun reconnectBotInChannel(bot: CourseBot, channel: String): CompletableFuture<Unit> {
        return bot.part(channel).thenCompose { bot.join(channel) }
    }

    @Test
    fun `bot start counting on channel, then we restore the bot and count the messages`() {
        courseApp.login("aviad", "hunter2")
                .thenCompose { adminToken -> courseApp.channelJoin(adminToken, "#channel").thenApply { adminToken } }.join()

        bots.bot()
                .thenCompose { bot ->
                    bot.join("#channel").thenApply { bot }.thenCompose { bot.beginCount("#channel", "hello", MediaType.TEXT) }
                }
                .thenCompose { courseApp.login("shahar", "pass").thenCompose { token -> joinChannelAndSendAsUser("#channel", "hello", token) } }.join()



        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count("#channel", "hello", MediaType.TEXT) }.get()
        }, equalTo(1L))


    }

    @Test
    fun `A user in the channel can ask the bot to do calculation- checking parentheses precedence`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate (20 * (2 + 2)/2)+1".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == (20 * (2 + 2) / 2) + 1 })
        }
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation- checking arithmetic precedence`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 1+2 * 2/2+1".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == ((20 * 1) + ((2 * 2) / 2) + 1) })
        }
    }


    @Test
    fun `A user in the channel can ask the bot to do calculation with bad regex pattern`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate [20 * 1+2 * 2/2+1]".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())

        }
        verify(exactly = 0) { listener.invoke("#channel@Anna0", any()) }
    }

    @Test
    fun `Can create a bot and add make it join channels`() {
        val token = courseApp.login("gal", "hunter2").join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            val bot = courseApp.channelJoin(token, "#channel")
                    .thenCompose { bots.bot() }
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

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bots("#channel").join()
        }, equalTo(listOf("Anna0")))
    }

    @Test
    fun `A user in the channel can ask the bot to do calculation`() {
        val listener = mockk<ListenerCallback>(relaxed = true)
        every { listener(any(), any()) } returns ImmediateFuture { }

        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot().thenCompose { bot ->
                                    bot.join("#channel")
                                            .thenCompose { bot.setCalculationTrigger("calculate") }
                                }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token -> courseApp.addListener(token, listener).thenApply { token } }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "calculate 20 * 2 + 2".toByteArray()).join()) }
                }.join()

        verify {
            listener.invoke("#channel@matan", any())
            listener.invoke("#channel@Anna0", match { String(it.contents).toInt() == 42 })
        }
    }

    @Test
    fun `A user in the channel can tip another user`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot()
                                        .thenCompose { bot ->
                                            bot.join("#channel").thenApply { bot }
                                        }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token ->
                                courseApp.channelJoin(token, "#channel").thenApply { token }
                            }
                            .thenCompose { token -> courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join()) }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("gal")))
    }

    fun joinChannelAndSendAsUser(channel: String, content: String, userToken: String): CompletableFuture<Unit> {
        return courseApp.channelJoin(userToken, channel)
                .thenCompose { courseApp.channelSend(userToken, channel,
                        messageFactory.create(MediaType.TEXT, content.toByteArray()).get()) }
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
                            .thenCompose { courseApp.login("matan", "pass").thenCompose { token -> joinChannelAndSendAsUser(channel, "hello, world!", token) } }
                }.join()

        assertThat(runWithTimeout(ofSeconds(10)) {
            bots.bot("Anna0").thenCompose { bot -> bot.count(regex = regex) }.join()
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

        assertThat(runWithTimeout(ofSeconds(10)) {
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
