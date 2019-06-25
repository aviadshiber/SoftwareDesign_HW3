package il.ac.technion.cs.softwaredesign.models

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class BotModel : BaseModel() {
    companion object {
        const val TYPE = "bot"
        const val KEY_BOT_ID = "botId"
        const val KEY_BOT_NAME = "botName"
        const val KEY_BOT_TOKEN = "botToken"
        const val KEY_BOT_LAST_SEEN_MSG_TIME = "lastSeenMessageTime"
        const val KEY_BOT_CALCULATION_TRIGGER = "calculationTrigger"
        const val KEY_BOT_TIP_TRIGGER = "tipTrigger"
        const val KEY_BOT_MOST_ACTIVE_USER = "mostActiveUser"
        const val KEY_BOT_MOST_ACTIVE_USER_COUNT = "mostActiveUserCount"

        const val LIST_BOT_CHANNELS = "botChannels"
    }

    var botId: Long = -1L
    lateinit var botName: String
    lateinit var botToken: String
    lateinit var lastSeenMessageTime: String
    lateinit var calculationTrigger: String
    lateinit var tipTrigger: String
    lateinit var mostActiveUser: String
    var mostActiveUserCount: Long = -1L
}