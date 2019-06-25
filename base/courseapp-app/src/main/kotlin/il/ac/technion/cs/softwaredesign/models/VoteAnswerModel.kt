package il.ac.technion.cs.softwaredesign.models

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class VoteAnswerModel : BaseModel() {
    companion object {
        const val TYPE = "voteAnswer"
        const val KEY_ANSWER_INDEX = "answerIndex"
    }

    var answerIndex: Long = -1L
}