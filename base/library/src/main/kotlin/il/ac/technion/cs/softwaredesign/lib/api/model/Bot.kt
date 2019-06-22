package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class Bot : BaseModel() {
    companion object {
        const val TYPE = "bot"
        const val KEY_BOT_ID = "botId"
        const val KEY_BOT_NAME = "botName"
        const val KEY_BOT_TOKEN = "botToken"
        const val KEY_BOT_LAST_SEEN_MSG_TIME = "lastSeenMessageTime"
        const val KEY_BOT_CALCULATION_TRIGGER = "calculationTrigger"

        const val LIST_BOT_CHANNELS = "botChannels"
    }

    var botId: Long = -1L
    lateinit var botName: String
    lateinit var botToken: String
    lateinit var lastSeenMessageTime: String
    lateinit var calculationTrigger: String
}