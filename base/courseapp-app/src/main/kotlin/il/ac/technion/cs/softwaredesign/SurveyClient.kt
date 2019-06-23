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

    fun createQuestion(q: Question): CompletableFuture<SurveyClient> {
        return botApi.createSurvey(id, q).thenApply { this }
    }

    fun putAnswers(answers: List<Answer>): CompletableFuture<SurveyClient> {
        return answers
                .mapComposeIndexList { index, answer ->
                    answersTree.treeInsert(Survey.LIST_ANSWERS, id, GenericKeyPair(index.toLong(), answer))
                }.thenApply { this }
    }

    fun getAnswers(): CompletableFuture<List<Answer>> {
        return answersTree.treeToSequence(Survey.LIST_ANSWERS, id).thenApply {
            it.map { (genKey, _) -> genKey.getSecond() }.toList()
        }
    }

    fun voteForAnswer(answer: Answer, votingUser: UserName): CompletableFuture<SurveyClient> {
        return getAnswers().thenApply { it.indexOf(answer) }.thenCompose {
            if (it >= 0) upsertUserVote(votingUser, it.toLong()).thenApply { this } //todo: change to override of answer
            else ImmediateFuture { this }
        }
    }

    fun getVotes(): CompletableFuture<List<AnswerCount>> {
        TODO()
    }

    /* fun updateCountersUserVote(prevIndex:Long?,currentIndex:Long,u:UserName):CompletableFuture<SurveyClient>{
         return if(prevIndex==currentIndex) ImmediateFuture { this }
         else if(prevIndex==null) upsertUserVote(u,currentIndex)
     }*/

    /**
     * The method update or insert a vote index for a user
     * @return the previous vote index
     */
    private fun upsertUserVote(u: UserName, index: Long): CompletableFuture<Long?> {
        val voteId = "${id}_$u"
        return botApi.findVoteAnswer(voteId).thenCompose<Long?> { prevVote ->
            if (prevVote == null) botApi.createVoteAnswer(voteId, index).thenApply { null }
            else botApi.updateVoteAnswer(voteId, index).thenApply { prevVote.answerIndex }
        }
    }

    private fun incCounter(index: Long): CompletableFuture<Long> {
        return upsertVoteCounter(index, 1L)
    }

    private fun decCounter(index: Long): CompletableFuture<Long> {
        return upsertVoteCounter(index, -1L)
    }

    private fun upsertVoteCounter(index: Long, number: Long): CompletableFuture<Long> {
        val counterId = "${id}_$index"
        return botApi.findCounter(counterId).thenCompose<Long> { prevCounter ->
            if (prevCounter == null) botApi.createCounter(counterId).thenCompose { botApi.updateCounter(counterId, number) }
                    .thenApply { 0L }
            else botApi.updateCounter(counterId, prevCounter.value + number).thenApply { prevCounter.value }
        }
    }






}