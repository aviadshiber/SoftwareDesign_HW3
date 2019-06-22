package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

/**
 * Describes a channel in the application
 */
class Channel: BaseModel() {

    companion object {
        const val TYPE = "channel"
        const val KEY_NAME = "name"
        const val KEY_CHANNEL_ID = "channelId"
        /**
         * Holds the list of operators for a given channel
         *
         */
        const val LIST_OPERATORS = "operators"
        /**
         * Holds the list of joined users for a given channel
         *
         */
        const val LIST_USERS = "users"
        /**
         * Holds the list of joined bots for a given channel
         *
         */
        const val LIST_BOTS = "channelBots"

        /**
         * list of valid pairs of (mediaType, regex)
         */
        const val LIST_CHANNEL_MSG_COUNTERS_SETTINGS = "channelMsgCountersSettings"
    }

    lateinit var name: String
    var channelId: Long = -1L

}