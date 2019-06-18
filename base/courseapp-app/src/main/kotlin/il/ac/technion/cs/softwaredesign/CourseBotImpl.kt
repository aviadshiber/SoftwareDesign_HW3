package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotImpl(private val token: String, private val id: Long, private val name: String, private val courseApp: CourseApp)
    : CourseBot {


    private val String.channelName: String
        get() {
            return this.substringBefore("@", "")
        }
    private var lastSeenMessageTime: LocalDateTime? = null
    private val lastSeenCallback: ListenerCallback = { source: String, message: Message ->
        ImmediateFuture {
            if (wasNewMessageCreated(message) && isChannelName(source))
                lastSeenMessageTime = message.created
        }

    }

    init {
        /*
        TODO: 1.load all listeners from storage
              2.use tree for channels bot is in

         */
    }

    private fun isChannelName(s: String) = channelNameRule matches s.channelName
    private fun wasNewMessageCreated(message: Message) =
            lastSeenMessageTime == null || message.created > lastSeenMessageTime

    override fun join(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelJoin(token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenApply { /* todo add channel to tree */ }
                .thenCompose { courseApp.addListener(token, lastSeenCallback) }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelPart(token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenApply { /* todo remove channel from tree */ }
                .thenCompose { courseApp.removeListener(token, lastSeenCallback) }
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

    internal companion object {
        val channelNameRule: Regex = Regex("#[#_A-Za-z0-9]*")
    }
}