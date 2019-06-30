package il.ac.technion.cs.softwaredesign.tests.others.vlad

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.present
import il.ac.technion.cs.softwaredesign.messages.MediaType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseBotWithRebootTest : CourseBotTest() {
    override fun reboot() {
        doReboot()
        bot = bots.bot("Anna0").join()
    }

    @Nested
    inner class JoinPartTest : CourseBotTest.JoinPartTest()

    @Nested
    inner class CountTest : CourseBotTest.CountTest() {
        @Test
        fun `bot continue counting messages after reboot`() {
            val channel = "#disturbed"
            courseApp.channelJoin(adminToken, channel).join()
            bot.join(channel).join()
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")
            reboot()
            sendMessageToChannel(adminToken, channel, MediaType.TEXT, "42")
            val token = loginAndJoin("user", channel)
            sendMessageToChannel(token, channel, MediaType.TEXT, "42")
            sendMessageToChannel(token, channel, MediaType.TEXT, "42")
            sendMessageToChannel(token, channel, MediaType.TEXT, "42")
            reboot()
            assertThat(bot.mostActiveUser(channel).join(), present(equalTo("user")))
        }
    }

    @Nested
    inner class TipTest : CourseBotTest.TipTest() {
        @Test
        fun `bot continue counting tip after reboot`() {
            val channel = "#disturbed"
            val trigger = "tip"

            courseApp.channelJoin(adminToken, channel).join()
            val token = loginAndJoin("user", channel)
            bot.join(channel).join()
            bot.setTipTrigger(trigger).join()
            tip(token, 42L, "admin", channel, trigger)
            reboot()
            tip(adminToken, 43L, "user", channel, trigger)
            reboot()
            assertThat(bot.richestUser(channel).join(), present(equalTo("user")))
        }
    }

    @Nested
    inner class CalculationTest : CourseBotTest.CalculationTest()

    @Nested
    inner class SeenTimeTest : CourseBotTest.SeenTimeTest()

    @Nested
    inner class ActiveTest : CourseBotTest.ActiveTest()

    @Nested
    inner class SurveyTest : CourseBotTest.SurveyTest()
}