package il.ac.technion.cs.softwaredesign.tests

import com.natpryce.hamkrest.assertion.assertThat
import il.ac.technion.cs.softwaredesign.CourseApp
// TODO: Optimize imports, some of these look weird.
import java.util.ArrayList
import java.util.HashMap
import java.io.BufferedReader
import java.io.FileReader
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration
import org.junit.jupiter.api.assertThrows


enum class CsvIdx(val idx: Int) {
    USER_NAME_IDX(0),
    PASS_IDX(1),
    IS_LOGGED_OUT_IDX(2)
}

data class User(val id: String,
                val pass: String,
                val isLoggedOut: Boolean) {
    var token: String = ""
}


fun readCsv(fileName: String): List<User> {
    var userData = ArrayList<User>()
    var line: String?
    var fileReader = BufferedReader(FileReader(fileName))
    val topLine = fileReader.readLine()
    line = fileReader.readLine()
    while (line != null) {

        val tokens = line.split(',')
        if (tokens.isNotEmpty()) {
            val user = User(
                    id = tokens[CsvIdx.USER_NAME_IDX.idx],
                    pass = tokens[CsvIdx.PASS_IDX.idx],
                    isLoggedOut = tokens[CsvIdx.IS_LOGGED_OUT_IDX.idx] == "1"
            )
            userData.add(user)
        }
        line = fileReader.readLine()
    }

    return userData
}


fun buildUserIdToTokenMap(courseApp: CourseApp, fileName: String): Map<String, String> {
    val userData = readCsv(fileName)
    var userToTokenMap = HashMap<String, String>()

    for (user in userData) {
        val token = courseApp.login(user.id, user.pass).join()

        userToTokenMap.put(user.id, token)
    }

    userData.stream().filter({ u -> u.isLoggedOut }).forEach(
            { u -> courseApp.logout(userToTokenMap.get(u.id) as String).join() }
    )
    return userToTokenMap
}

//val isTrue = equalTo(true)
//val isFalse = equalTo(false)
fun assertTrue(pred: Boolean): Unit = assertThat(pred, isTrue)
//fun <T> runWithTimeout(timeout: Duration, executable: () -> T): T =
//        assertTimeoutPreemptively(timeout, ThrowingSupplier(executable))

fun assertWithTimeout(executable: () -> Unit, timeout: Duration = Duration.ofSeconds(10)): Unit =
        runWithTimeout(timeout, executable)

