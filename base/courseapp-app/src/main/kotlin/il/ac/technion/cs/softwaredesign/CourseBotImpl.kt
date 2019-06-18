package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotImpl(private val bot: Bot, private val courseApp: CourseApp)
    : CourseBot {


    /* TODO: replace by tree impl in future */
    private fun addChannel(channelName: String) = bot.channels.add(channelName)

    private fun removeChannel(channelName: String) = bot.channels.remove(channelName)


    init {
        /*
        TODO: 1.load all listeners from storage
              2.use tree for channels bot is in

         */
    }

    private fun isChannelName(s: String) = channelNameRule matches s.channelName
    private fun wasNewMessageCreated(message: Message) =
            bot.lastSeenMessageTime == null || message.created > bot.lastSeenMessageTime

    override fun join(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelJoin(bot.token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenApply { addChannel(channelName) }
                .thenCompose { courseApp.addListener(bot.token, lastSeenCallback) }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        //todo: clean statistics
        return courseApp.channelPart(bot.token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenApply { removeChannel(channelName) }
                .thenCompose { courseApp.removeListener(bot.token, lastSeenCallback) }
    }


    override fun channels(): CompletableFuture<List<String>> {
        return ImmediateFuture { bot.channels } //TODO: replace with storage
    }


    override fun beginCount(regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        if (regex == null && mediaType == null) ImmediateFuture { throw IllegalArgumentException() }
        val countCallback: ListenerCallback = { source: String, message: Message ->
            if (shouldBeCounted(regex, mediaType, source, message)) {
                getCounter(source.channelName, regex, mediaType)
                        .thenCompose { counter ->
                            if (counter == null) setCounter(source.channelName, regex, mediaType, 0)
                            else setCounter(source.channelName, regex, mediaType, counter + 1)
                        }

            } else ImmediateFuture { }
        }
        return courseApp.addListener(bot.token, countCallback)
    }


    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        return getCounter(channel, regex, mediaType).thenApply { it ?: throw IllegalArgumentException() }
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        return ImmediateFuture { bot.lastSeenMessageTime }
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

    private val String.channelName: String
        get() {
            return this.substringBefore("@", "")
        }

    private val lastSeenCallback: ListenerCallback = { source: String, message: Message ->
        ImmediateFuture {
            if (wasNewMessageCreated(message) && isChannelName(source))
                bot.lastSeenMessageTime = message.created
        }
    }

    private fun shouldBeCounted(regex: String?, mediaType: MediaType?, source: String, message: Message) =
            (isRegexMatchesMessageContent(regex, message) ||
                    isMessageMediaTypeMatch(mediaType, message)) && isChannelName(source)

    private fun isMessageMediaTypeMatch(mediaType: MediaType?, message: Message) =
            mediaType != null && message.media == mediaType

    private fun isRegexMatchesMessageContent(regex: String?, message: Message) =
            (regex != null && Regex(regex) matches String(message.contents))


    /**
     * the method gets the counter from storage with the following pattern:
     * channelName_regex_mediaType.oridinal -> counter
     * channelName is null to count all channels (iterator over channels of bots)
     * null is returned if no counter was initiated yet.
     */
    private fun getCounter(channelName: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long?> {
        TODO("not implemented")
    }

    /**
     * the method set the counter with of the following pattern
     * channelName_regex_mediaType.oridinal -> x
     */
    private fun setCounter(channelName: String, regex: String?, mediaType: MediaType?, x: Long): CompletableFuture<Unit> {
        TODO("not implemented")
    }


    internal companion object {
        val channelNameRule: Regex = Regex("#[#_A-Za-z0-9]*")
    }
}