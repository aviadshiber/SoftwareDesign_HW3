package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.authzee.kotlinguice4.getInstance
import com.google.inject.Guice
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.*
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.tests.TestModule
import il.ac.technion.cs.softwaredesign.tests.joinException
import il.ac.technion.cs.softwaredesign.tests.runWithTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class CourseBotTestMixed {
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

    @AfterEach
    fun clearDb() {
        FakeStorage("botNames".toByteArray()).clear()
        FakeStorage("botsNameToData".toByteArray()).clear()
        FakeStorage("channelNameToUserNames".toByteArray()).clear()
        FakeStorage("userNameToBits".toByteArray()).clear()
    }

    @Test
    fun `bot can create a channel`() {
        val bot = bots.bot().join()
        courseApp.channelJoin("Anna0", "#channel").join()

        assert(bot.channels().join().isEmpty() == true)

        assertDoesNotThrow {
            bot.join("#channel").joinException()
        }
    }

    //    @Test
    fun `bot can become admin`() {
        val (userToken, bot) = bots.bot("jon")
                .thenCompose { bot ->
                    courseApp.channelJoin("jon", "#channel")
                            .thenCompose { bot.join("#channel") }
                            .thenCompose {
                                courseApp.login("gal", "hunter2")
                                        .thenCompose { userToken ->
                                            courseApp.channelJoin("gal", "#channel")
                                                    .thenApply { Pair(userToken, bot) }
                                        }
                            }
                }.join()

        assertThrows<UserNotAuthorizedException> {
            courseApp.channelMakeOperator(userToken, "#channel", "jon").joinException()
        }
    }

    @Test
    fun `tip user not in channel do nothing`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#dummy")
                            .thenCompose {
                                bots.bot("Anna0")
                                        .thenCompose { bot -> bot.join("#dummy").thenApply { bot } }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.channelPart(adminToken, "#dummy") }
                            .thenCompose { courseApp.channelJoin(adminToken, "#channel") }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token ->
                                courseApp.channelJoin(token, "#dummy").thenApply { token }
                                        .thenCompose {
                                            bots.bot("Anna0")
                                                    .thenCompose { bot -> bot.join("#channel") }
                                                    .thenApply { token }
                                        }
                            }
                            .thenCompose { token -> courseApp.channelSend(token, "#dummy", messageFactory.create(MediaType.TEXT, "tip 10 gal".toByteArray()).join()) }
                }.join()

        val richest = bots.bot("Anna0").thenCompose { it.richestUser("#channel") }.join()

        assert(richest == null)
    }

    @Test
    fun `user in the channel can tip multiple times another user`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot("Anna0")
                                        .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("jon", "snow") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token ->
                                courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 40 gal".toByteArray()).join())
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 jon".toByteArray()).join()) }
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 15 jon".toByteArray()).join()) }
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 20 jon".toByteArray()).join()) }
                            }
                }.join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bot("Anna0")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("jon")))
    }

    @Test
    fun `bot can be tipped`() {
        courseApp.login("gal", "hunter2")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot("jon")
                                        .thenCompose { bot -> bot.join("#channel").thenApply { bot } }
                                        .thenCompose { bot -> bot.setTipTrigger("tip") }
                            }
                            .thenCompose { courseApp.login("matan", "s3kr3t") }
                            .thenCompose { token -> courseApp.channelJoin(token, "#channel").thenApply { token } }
                            .thenCompose { token ->
                                courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 40 gal".toByteArray()).join())
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 10 jon".toByteArray()).join()) }
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 15 jon".toByteArray()).join()) }
                                        .thenCompose { courseApp.channelSend(token, "#channel", messageFactory.create(MediaType.TEXT, "tip 20 jon".toByteArray()).join()) }
                            }
                }.join()

        assertThat(runWithTimeout(Duration.ofSeconds(10)) {
            bots.bot("jon")
                    .thenCompose { it.richestUser("#channel") }
                    .join()
        }, present(equalTo("jon")))
    }

    @Test
    fun `bot can receive private message`() {
        val (adminToken, bot) = courseApp.login("admin", "admin")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot("jon")
                                        .thenCompose { bot ->
                                            bot.join("#channel")
                                                    .thenApply { Pair(adminToken, bot) }
                                        }
                            }
                }
                .join()

        assertDoesNotThrow {
            val message = sendMessageToUser(adminToken, "jon", MediaType.TEXT, "hi")
        }
    }

    @Test
    fun `bot can receive broadcast message`() {
        val (adminToken, bot) = courseApp.login("admin", "admin")
                .thenCompose { adminToken ->
                    courseApp.channelJoin(adminToken, "#channel")
                            .thenCompose {
                                bots.bot("jon")
                                        .thenCompose { bot ->
                                            bot.join("#channel")
                                                    .thenApply { Pair(adminToken, bot) }
                                        }
                            }
                }
                .join()

        assertDoesNotThrow {
            val message = sendMessageBroadcast(adminToken, MediaType.TEXT, "hi")
        }
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
}
