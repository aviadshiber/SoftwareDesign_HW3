package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Survey
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.mapComposeIndexList
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture

typealias Answer = String
typealias Question = String
typealias AnswerCount = Long
typealias UserName = String

class SurveyClient constructor(surveyId: Long, private val botApi: CourseBotApi) {
    //surevyId_username-> answerIndex
    // surevyId_answerIndex -> counter


    val answersTree = TreeWrapper(botApi, "surveyAnswers")
    val id = surveyId.toString()
    var question: Question
        get() {
            return botApi.findSurvey(id).thenApply { it!!.question }.join()
        }
        set(value) {
            botApi.updateSurvey(id, Pair(Survey.KEY_QUESTION, value)).thenDispose().join()
        }

    var noAnswers: Long
        get() {
            return botApi.findSurvey(id).thenApply { it!!.noAnswers }.join()
        }
        set(value) {
            botApi.updateSurvey(id, Pair(Survey.KEY_NO_ANSWERS, value)).thenDispose().join()
        }

    fun createQuestion(q: Question): CompletableFuture<SurveyClient> {
        return botApi.createSurvey(id, q).thenApply { this }
    }

    fun putAnswers(answers: List<Answer>): CompletableFuture<SurveyClient> {
        return answers
                .mapComposeIndexList { index, answer ->
                    answersTree.treeInsert(Survey.LIST_ANSWERS, id, GenericKeyPair(index.toLong(), answer))
                }.thenApply { noAnswers = answers.size.toLong() }
                .thenApply { this }
    }

    fun getAnswers(): CompletableFuture<List<Answer>> {
        return answersTree.treeToSequence(Survey.LIST_ANSWERS, id).thenApply {
            it.map { (genKey, _) -> genKey.getSecond() }.toList()
        }
    }

    fun voteForAnswer(answer: Answer, votingUser: UserName): CompletableFuture<SurveyClient> {
        return getAnswers().thenApply { it.indexOf(answer) }.thenCompose { currAnsIndex ->
            if (currAnsIndex >= 0)
                upsertUserLastAnswer(votingUser, currAnsIndex.toLong()) // this updates last user vote
                    .thenCompose { prevAnsIndex -> updateAnswersCounters(prevAnsIndex, currAnsIndex.toLong()) } // this update counter
            else ImmediateFuture { this }
        }
    }

    fun getVoteCounters(): List<AnswerCount> {
        return (0 until noAnswers).toList().map{ ansIndex ->
            val counterId = createCounterId(ansIndex)
            botApi.findCounter(counterId).thenApply { it?.value ?: 0 }.join()
        }
    }

     private fun updateAnswersCounters(prevIndex:Long?, currentIndex:Long):CompletableFuture<SurveyClient>{
         return if (prevIndex==currentIndex) ImmediateFuture { this }
         else if (prevIndex==null) incCounter(currentIndex).thenApply { this }
         else incCounter(currentIndex).thenCompose { decCounter(prevIndex) }.thenApply { this }
     }

    /**
     * The method update or insert a vote index for a user
     * @return the previous vote index
     */
    private fun upsertUserLastAnswer(u: UserName, index: Long): CompletableFuture<Long?> {
        val voteId = "${id}_$u"
        return botApi.findVoteAnswer(voteId).thenCompose<Long?> { prevVote ->
            if (prevVote == null) botApi.createVoteAnswer(voteId, index).thenApply { null }
            else botApi.updateVoteAnswer(voteId, index).thenApply { prevVote.answerIndex }
        }
    }

    private fun incCounter(index: Long): CompletableFuture<Long> = upsertVoteCounter(index, 1L)

    private fun decCounter(index: Long): CompletableFuture<Long> = upsertVoteCounter(index, -1L)

    private fun createCounterId(index: Long) = "${id}_$index"

    private fun extractIndexFromCounterId(counterId: String) = counterId.substringAfter("_")

    private fun upsertVoteCounter(index: Long, number: Long): CompletableFuture<Long> {
        val counterId = createCounterId(index)
        return botApi.findCounter(counterId).thenCompose<Long> { prevCounter ->
            if (prevCounter == null) botApi.createCounter(counterId).thenCompose { botApi.updateCounter(counterId, number) }
                    .thenApply { 0L }
            else botApi.updateCounter(counterId, prevCounter.value + number).thenApply { prevCounter.value }
        }
    }






}