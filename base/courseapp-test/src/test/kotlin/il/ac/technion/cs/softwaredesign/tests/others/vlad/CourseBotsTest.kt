package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
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

}