package il.ac.technion.cs.softwaredesign.tests.others.vlad

import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseBotsWithRebootTest : CourseBotsTest() {
    override fun reboot() {
        doReboot()
    }
}