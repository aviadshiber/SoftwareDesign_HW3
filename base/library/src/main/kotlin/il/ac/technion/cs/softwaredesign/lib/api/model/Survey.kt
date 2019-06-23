package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class Survey : BaseModel() {
    companion object {
        const val TYPE = "survey"
        const val KEY_QUESTION = "question"
        const val KEY_NO_ANSWERS = "noAnswers"

        const val LIST_ANSWERS = "answers"
    }

    lateinit var question: String
    var noAnswers: Long = 0L
}