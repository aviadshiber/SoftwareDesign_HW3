package il.ac.technion.cs.softwaredesign.services

import il.ac.technion.cs.softwaredesign.models.BotModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Bot constructor(val id: Long, val token: String, val name: String, private val courseBotApi: CourseBotApi) {

    // TODO: check if we can use delete instead of invalid_value
    companion object {
        const val invalid_value = ""
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss:SSS")
    }

    var lastSeenMessageTime: LocalDateTime?
        get() {
            return courseBotApi.findBot(name).thenApply { it!!.lastSeenMessageTime }
                    .thenApply { if (it == invalid_value) null else stringToLocalDateTime(it) }.join()
        }
        set(value) {
            val valueToWrite = if (value == null) invalid_value else localDateTimeToString(value)
            courseBotApi.updateBot(name, Pair(BotModel.KEY_BOT_LAST_SEEN_MSG_TIME, valueToWrite)).thenApply { }.join()
        }

    var calculationTrigger: String?
        get() {
            return courseBotApi.findBot(name).thenApply { it!!.calculationTrigger }
                    .thenApply { if (it == invalid_value) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: invalid_value
            courseBotApi.updateBot(name, Pair(BotModel.KEY_BOT_CALCULATION_TRIGGER, valueToWrite)).thenApply { }.join()
        }

    var tipTrigger: String?
        get() {
            return courseBotApi.findBot(name).thenApply { it!!.tipTrigger }
                    .thenApply { if (it == invalid_value) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: invalid_value
            courseBotApi.updateBot(name, Pair(BotModel.KEY_BOT_TIP_TRIGGER, valueToWrite)).thenApply { }.join()
        }

    var mostActiveUser: String?
        get() {
            return courseBotApi.findBot(name).thenApply { it!!.mostActiveUser }
                    .thenApply { if (it == invalid_value) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: invalid_value
            courseBotApi.updateBot(name, Pair(BotModel.KEY_BOT_MOST_ACTIVE_USER, valueToWrite)).thenApply { }.join()
        }

    var mostActiveUserCount: Long?
        get() {
            return courseBotApi.findBot(name).thenApply { it!!.mostActiveUserCount }
                    .thenApply { if (it == -1L) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: -1L
            courseBotApi.updateBot(name, Pair(BotModel.KEY_BOT_MOST_ACTIVE_USER_COUNT, valueToWrite)).thenApply { }.join()
        }

    //val channels = mutableListOf<String>()

    private fun localDateTimeToString(localDateTime: LocalDateTime): String {
        return localDateTime.format(formatter)
    }

    private fun stringToLocalDateTime(s: String): LocalDateTime {
        return LocalDateTime.parse(s, formatter)
    }
}