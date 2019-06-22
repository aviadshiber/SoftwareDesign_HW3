package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel
import java.time.LocalDateTime

class Bot : BaseModel() {
    companion object {
        const val TYPE = "bot"
        const val KEY_BOT_ID = "botId"
        const val KEY_BOT_NAME = "botName"
        const val KEY_BOT_TOKEN = "botToken"
        const val KEY_BOT_LAST_SEEN_MSG_TIME = "lastSeenMessageTime"
        const val KEY_BOT_CALCULATION_TRIGGER = "calculationTrigger"

        const val LIST_BOT_CHANNELS = "botChannels"
        const val LIST_MSG_COUNTERS_SETTINGS = "msgCountersSettings"
    }

    var botId: Long = -1L
    lateinit var botName: String
    lateinit var botToken: String
    var lastSeenMessageTime: LocalDateTime? = null
    var calculationTrigger: String? = null
}