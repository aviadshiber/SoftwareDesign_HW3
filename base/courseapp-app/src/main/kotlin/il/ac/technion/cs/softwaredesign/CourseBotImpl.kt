package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.messages.MediaType
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotImpl : CourseBot {


    override fun join(channelName: String): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channels(): CompletableFuture<List<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun beginCount(regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}