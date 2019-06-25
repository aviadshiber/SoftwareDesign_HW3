package il.ac.technion.cs.softwaredesign.models

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class BotsModel : BaseModel() {
    companion object {
        const val TYPE = "__metadata"
        const val ALL_BOTS = "allBots"
        const val ALL_CHANNELS = "allChannels"
    }

}