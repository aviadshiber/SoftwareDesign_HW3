package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.expressionsolver.Value
import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Bot
import il.ac.technion.cs.softwaredesign.lib.api.model.BotsMetadata
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KMutableProperty1

class CourseBotImpl(private val bot: Bot, private val courseApp: CourseApp, private val messageFactory: MessageFactory,
                    private val courseBotApi: CourseBotApi
)
    : CourseBot {

    internal companion object {
        val channelNameRule = "#[#_A-Za-z0-9]*".toRegex()
        private const val botsMetadataName = "allBots"
    }

    private fun createChannelIfNotExist(channelName: String): CompletableFuture<Long> {
        return courseBotApi.findChannel(channelName)
                .thenCompose {
                    if (it == null) {
                        generateChannelId()
                            .thenCompose { id ->
                                courseBotApi.createChannel(channelName, "courseBot", id).thenApply { id } // TODO: implement, similer to BotsImpl
                                    .thenCompose { id ->
                                        courseBotApi.listInsert(BotsMetadata.ALL_CHANNELS, botsMetadataName, channelName).thenApply { id }
                                    }
                            }
                    }
                    else ImmediateFuture { it.channelId }
                }
    }

    private fun generateChannelId(): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun addBotToChannel(channelName: String) =
            courseBotApi.listInsert(Channel.LIST_BOTS, channelName, Pair(bot.botId, bot.botName).pairToString())

    private fun removeBotFromChannel(channelName: String) =
            courseBotApi.listRemove(Channel.LIST_BOTS, channelName, Pair(bot.botId, bot.botName).pairToString())

    private fun addChannelToBot(channelName: String, channelId: Long) =
            courseBotApi.listInsert(Bot.LIST_BOT_CHANNELS, bot.botName, Pair(channelId, channelName).pairToString())

    private fun removeChannelFromBot(channelName: String, channelId: Long) =
            courseBotApi.listRemove(Bot.LIST_BOT_CHANNELS, bot.botName, Pair(channelId, channelName).pairToString())


    init {
        /*
        TODO: 1.load all listeners from storage
              2.use tree for channels bot is in

         */
    }

    private fun isChannelNameValid(s: String) = channelNameRule matches s.channelName
    private fun isNewMessageByCreationTime(message: Message) =
            bot.lastSeenMessageTime == null || message.created > bot.lastSeenMessageTime

    override fun join(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelJoin(bot.botToken, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenCompose { createChannelIfNotExist(channelName) }
                .thenCompose { channelId -> addChannelToBot(channelName, channelId) }
                .thenCompose { addBotToChannel(channelName) }
                .thenCompose { courseApp.addListener(bot.botToken, lastSeenCallback) } //TODO: add listener to storage
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        //todo: clean statistics (put null in all counters ['begin count'])
        return courseApp.channelPart(bot.botToken, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenApply { removeChannel(channelName) }
                .thenCompose { courseApp.removeListener(bot.botToken, lastSeenCallback) } //TODO: remove listener to storage
    }


    override fun channels(): CompletableFuture<List<String>> {
        return ImmediateFuture { bot.channels } //TODO: replace with storage
    }


    override fun beginCount(regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {

        //todo: iterator over all channels and setCounter to zero

        if (regex == null && mediaType == null) ImmediateFuture { throw IllegalArgumentException() } //TODO: ask matan if throw without future
        val countCallback: ListenerCallback = { source: String, message: Message ->
            if (shouldBeCountMessage(regex, mediaType, source, message)) {
                getCounter(source.channelName, regex, mediaType)
                        .thenCompose { counter ->
                            // if (counter == null) setCounter(source.channelName, regex, mediaType, 1)
                            //else
                            setCounter(source.channelName, regex, mediaType, counter!! + 1)
                        }

            } else ImmediateFuture { }
        }
        return courseApp.addListener(bot.botToken, countCallback) //TODO: add listener to storage
    }


    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        return getCounter(channel, regex, mediaType).thenApply { it ?: throw IllegalArgumentException() }
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = "$trigger <[()\\d*+/-\\s]+>".toRegex()
        return setCallBackTrigger(Bot::calculationTrigger, trigger, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val expression = regex.matchEntire(content)!!.groups[1]!!.value
            val solution = Value(expression).resolve()
            val messageFuture = messageFactory.create(MediaType.TEXT, "$solution".toByteArray())
            messageFuture.thenCompose { courseApp.channelSend(bot.botToken, source.channelName, it) }
        }
    }

    private fun setCallBackTrigger(prop: KMutableProperty1<Bot, String?>, trigger: String?, r: Regex, action: (source: String, message: Message) -> CompletableFuture<Unit>)
            : CompletableFuture<String?> {
        val prev = prop.get(bot)
        prop.set(bot, trigger)
        val triggerCallback: ListenerCallback = { source: String, message: Message ->
            val content = String(message.contents)
            if (isChannelNameValid(source) && trigger != null && r matches content) action(source, message)
            else ImmediateFuture { }
        }
        return courseApp.addListener(bot.botToken, triggerCallback).thenApply { prev } //TODO: add listener to storage
    }


    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        TODO("not implemented")
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
            if (isNewMessageByCreationTime(message) && isChannelNameValid(source))
                bot.lastSeenMessageTime = message.created
        }
    }

    private fun shouldBeCountMessage(regex: String?, mediaType: MediaType?, source: String, message: Message): Boolean {
        if (!isChannelNameValid(source)) return false
        return if (regex != null && mediaType != null)
            isMessageMediaTypeMatch(mediaType, message) && isRegexMatchesMessageContent(regex, message)
        else {
            isMessageMediaTypeMatch(mediaType, message) || isRegexMatchesMessageContent(regex, message)
        }
    }

    private fun isMessageMediaTypeMatch(mediaType: MediaType?, message: Message) =
            mediaType != null && message.media == mediaType

    private fun isRegexMatchesMessageContent(regex: String?, message: Message) =
            (regex != null && Regex(regex) matches String(message.contents))


    /**
     * the method gets the counter from storage with the following pattern:
     * channelName_regex_mediaType.oridinal -> counter
     * channelName is null to count all channels (iterate over channels of bots)
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


    private fun Pair<Long, String>.pairToString() = "$first,$second"
    private fun String.stringToPair(): Pair<Long, String>{
        val values = this.split(',')
        return Pair(values[0].toLong(), values[1])
    }
}