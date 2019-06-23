package il.ac.technion.cs.softwaredesign.lib.api.model

import il.ac.technion.cs.softwaredesign.lib.db.BaseModel

class Survey : BaseModel() {
    companion object {
        const val TYPE = "survey"
        const val KEY_SURVEY_ID = "surveyId"
        const val KEY_QUESTION = "question"


        const val LIST_ANSWERS = "answers"
    }

    var surveyId: Long = -1L
    lateinit var question: String
}