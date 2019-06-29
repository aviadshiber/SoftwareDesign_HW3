package il.ac.technion.cs.softwaredesign.services

import il.ac.technion.cs.softwaredesign.models.BotModel
import il.ac.technion.cs.softwaredesign.utils.convertToLocalDateTime
import il.ac.technion.cs.softwaredesign.utils.convertToString
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Bot constructor(val id: Long, val token: String, val name: String, private val botApi: CourseBotApi) {

    // TODO: check if we can use delete instead of invalid_value
    companion object {
        const val invalid_value = ""
    }

    var lastSeenMessageTime: LocalDateTime?
        get() {
            return botApi.findBot(name).thenApply { it!!.lastSeenMessageTime }
                    .thenApply { if (it == invalid_value) null else it.convertToLocalDateTime() }.join()
        }
        set(value) {
            val valueToWrite = value?.convertToString() ?: invalid_value
            botApi.updateBot(name, Pair(BotModel.KEY_BOT_LAST_SEEN_MSG_TIME, valueToWrite)).thenApply { }.join()
        }

    var calculationTrigger: String?
        get() {
            return botApi.findBot(name).thenApply { it!!.calculationTrigger }
                    .thenApply { if (it == invalid_value) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: invalid_value
            botApi.updateBot(name, Pair(BotModel.KEY_BOT_CALCULATION_TRIGGER, valueToWrite)).thenApply { }.join()
        }

    var tipTrigger: String?
        get() {
            return botApi.findBot(name).thenApply { it!!.tipTrigger }
                    .thenApply { if (it == invalid_value) null else it }.join()
        }
        set(value) {
            val valueToWrite = value ?: invalid_value
            botApi.updateBot(name, Pair(BotModel.KEY_BOT_TIP_TRIGGER, valueToWrite)).thenApply { }.join()
        }

}