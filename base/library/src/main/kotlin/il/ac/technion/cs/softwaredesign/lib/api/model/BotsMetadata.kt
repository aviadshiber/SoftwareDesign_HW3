package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class BotsMetadata : BaseModel() {
    companion object {
        const val TYPE = "__metadata"
        const val ALL_BOTS = "allBots"
        const val ALL_CHANNELS = "allChannels"
    }

}