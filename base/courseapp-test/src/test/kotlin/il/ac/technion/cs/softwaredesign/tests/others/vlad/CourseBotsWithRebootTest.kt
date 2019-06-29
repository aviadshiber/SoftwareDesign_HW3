package il.ac.technion.cs.softwaredesign.tests

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseBotsWithRebootTest : CourseBotsTest() {
    override fun reboot() {
        doReboot()
    }
}