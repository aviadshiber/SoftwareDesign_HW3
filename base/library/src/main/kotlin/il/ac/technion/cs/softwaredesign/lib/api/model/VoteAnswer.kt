package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class VoteAnswer : BaseModel() {
    companion object {
        const val TYPE = "voteAnswer"
        const val KEY_ANSWER_INDEX = "answerIndex"
    }

    var answerIndex: Long = -1L
}