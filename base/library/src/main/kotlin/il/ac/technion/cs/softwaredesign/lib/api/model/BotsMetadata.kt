package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class BotsMetadata : BaseModel() {
    companion object {
        const val TYPE = "__metadata"
        const val ALL_BOTS = "allBots"
        const val ALL_CHANNELS = "allChannels"

        const val KEY_LAST_BOT_ID = "lastBotId"
    }

    var lastBotId: Long = -1L
}