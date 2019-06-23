package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Survey
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose
import java.util.concurrent.CompletableFuture

typealias Answer = String
typealias Question = String
typealias AnswerCount = Long
typealias UserName = String

class SurveyClient constructor(private val surveyId: Long, private val botApi: CourseBotApi) {
    //surevyId_username-> answerIndex
    // surevyId_answerIndex -> counter

    val id = surveyId.toString()
    var question: Question
        get() {
            return botApi.findSurvey(id).thenApply { it!!.question }.join()
        }
        set(value) {
            botApi.updateSurvey(id, Pair(Survey.KEY_QUESTION, value)).thenDispose().join()
        }

    fun createQuestion(q: Question): CompletableFuture<SurveyClient> {
        return botApi.createSurvey(id, q).thenApply { this }
    }

    fun putAnswers(answers: List<Answer>): CompletableFuture<SurveyClient> {
        TODO()
    }

    fun getAnswers(): CompletableFuture<List<Answer>> {
        TODO()
    }

    fun voteForAnswer(answer: Answer, votingUser: UserName): CompletableFuture<SurveyClient> {
        TODO("vote for user, a user can override his vote")
    }

    fun getVotes(): CompletableFuture<List<AnswerCount>> {
        TODO()
    }


}