package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import il.ac.technion.cs.softwaredesign.tests.containsElementsInOrder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
open class CourseBotsTest : CourseTest() {
    private val adminToken = courseApp.login("admin", "password").join()

    @Test
    fun `make bot without name`() {
        bots.bot().join()
        reboot()
        assertThat(bots.bots().join(), equalTo(listOf("Anna0")))
    }

    @Test
    fun `make bot without name twice`() {
        bots.bot().join()
        bots.bot().join()
        reboot()
        assertThat(bots.bots().join(), equalTo(listOf("Anna0", "Anna1")))
    }

    @Test
    fun `can make bot with name`() {
        bots.bot("bot").join()
        reboot()
        assertThat(bots.bots().join(), equalTo(listOf("bot")))
    }

    @Test
    fun `can make bot with name twice`() {
        bots.bot("bot").join()
        bots.bot("bot").join()
        reboot()
        assertThat(bots.bots().join(), equalTo(listOf("bot")))
    }

    @Test
    fun `bots sorted in creation not alphabet order`() {
        bots.bot("z").join()
        bots.bot("a").join()
        reboot()
        assertThat(bots.bots().join(), equalTo(listOf("z", "a")))
    }

    @Test
    fun `bots per channel in creation not alphabet order`() {
        val channelName = "#archenemy"
        courseApp.channelJoin(adminToken, channelName)
                .thenCompose { bots.bot("z") }
                .join()

        val bot1 = bots.bot("y").join()
        val bot2 = bots.bot("x").join()

        bot2
                .join(channelName)
                .thenCompose { bot1.join(channelName) }
                .join()
        reboot()
        assertThat(bots.bots(channelName).join(), equalTo(listOf("y", "x")))
    }

    @Test
    fun `channels does not contain the channel where bot was kicked from`() {
        val channelName = "#ar"

        courseApp.channelJoin(adminToken, channelName)
                .thenCompose { bots.bot() }
                .thenCompose { bot -> bot.join(channelName) }
                .thenCompose { courseApp.channelKick(adminToken, channelName, "Anna0") }
                .join()

        assertThat(bots.bot("Anna0").thenCompose { bot -> bot.channels() }.join(), equalTo(listOf()))
    }

    @Test
    fun `join join kick join is ok`() {
        val channelName1 = "#1"
        val channelName2 = "#2"

        val bot = courseApp.channelJoin(adminToken, channelName1)
                .thenCompose { courseApp.channelJoin(adminToken, channelName2) }
                .thenCompose { bots.bot() }
                .thenCompose { bot ->
                    bot.join(channelName1)
                            .thenCompose { bot.join(channelName2) }
                            .thenCompose { courseApp.channelKick(adminToken, channelName1, "Anna0") }
                            .thenCompose { bot.join(channelName1) }
                            .thenApply { bot }
                }
                .join()

        assertThat(bot.channels().join(), containsElementsInOrder(channelName2, channelName1))
    }

}