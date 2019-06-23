package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Survey
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose

class SurveyClient constructor(val surveyId: Long, private val botApi: CourseBotApi) {

    var question: String
        get() {
            return botApi.findSurvey(surveyId).thenApply { it!!.question }.join()
        }
        set(value) {
            botApi.updateSurvey(surveyId, Pair(Survey.KEY_QUESTION, value)).thenDispose().join()
        }

    fun putAnswers(answers: List<String>) {
        TODO()
    }

    fun voteForAnswer(answer: String) {
        TODO()
    }


}