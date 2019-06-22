package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.expressionsolver.Value
import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Bot
import il.ac.technion.cs.softwaredesign.lib.api.model.BotsMetadata
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.mapComposeList
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KMutableProperty1

class CourseBotImpl(private val bot: BotClient, private val courseApp: CourseApp, private val messageFactory: MessageFactory,
                    private val courseBotApi: CourseBotApi
)
    : CourseBot {

    internal companion object {
        val channelNameRule = "#[#_A-Za-z0-9]*".toRegex()
        private const val botsMetadataName = "allBots"
        private const val botsStorageName = "botsStorage"
    }

    private val channelTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "channel_")
    private val botTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")

    init {
        /*
        TODO: 1.load all listeners from storage
              2.use tree for channels bot is in
         */
    }

    // TODO: 2 options: list insert will get id.toString() or channel name
    // if name - long name can be a problem
    // if id - we need to cast it back to name
    private fun createChannelIfNotExist(channelName: String): CompletableFuture<Long> {
        return courseBotApi.findChannel(channelName)
            .thenCompose {
                if (it == null) {
                    generateChannelId()
                        .thenCompose { id ->
                            courseBotApi.createChannel(channelName, id).thenApply { id }
                        }.thenCompose { id ->
                            channelTreeWrapper.treeInsert(BotsMetadata.ALL_CHANNELS, botsMetadataName, GenericKeyPair(0L, channelName)).thenApply { id }
                        }
                }
                else ImmediateFuture { it.channelId }
            }
    }

    private fun getChannelId(channelName: String) = courseBotApi.findChannel(channelName).thenApply { it!!.channelId }

    private fun generateChannelId(): CompletableFuture<Long> {
        val key = "$botsMetadataName,${BotsMetadata.KEY_LAST_CHANNEL_ID}"
        return courseBotApi.findMetadata(botsStorageName, key)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createMetadata(botsStorageName, key, 0L).thenApply { 0L }
                    else
                        courseBotApi.updateMetadata(botsStorageName, key, currId+1L)
                                .thenApply { currId+1L } }
    }

    // insert pair of (id, name) to keep the list sorted by generation time
    private fun addBotToChannel(channelName: String) =
            channelTreeWrapper.treeInsert(Channel.LIST_BOTS, channelName, GenericKeyPair(bot.id, bot.name))

    private fun removeBotFromChannel(channelName: String) =
            channelTreeWrapper.treeRemove(Channel.LIST_BOTS, channelName, GenericKeyPair(bot.id, bot.name))

    private fun addChannelToBot(channelName: String, channelId: Long) =
            botTreeWrapper.treeInsert(Bot.LIST_BOT_CHANNELS, bot.name, GenericKeyPair(channelId, channelName))

    private fun removeChannelFromBot(channelName: String, channelId: Long) =
            botTreeWrapper.treeRemove(Bot.LIST_BOT_CHANNELS, bot.name, GenericKeyPair(channelId, channelName))

    private fun isChannelNameValid(s: String) = channelNameRule matches s.channelName

    private fun isNewMessageByCreationTime(message: Message) =
            bot.lastSeenMessageTime == null || message.created > bot.lastSeenMessageTime

    override fun join(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelJoin(bot.token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenCompose { createChannelIfNotExist(channelName) }
                .thenCompose { channelId -> addChannelToBot(channelName, channelId) }
                .thenCompose { addBotToChannel(channelName) }
                .thenCompose { courseApp.addListener(bot.token, lastSeenCallback) } //TODO: add listener to storage
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        //todo: clean statistics (put null in all counters ['begin count'])
        // TOOD: which statistics exactly?
        return courseApp.channelPart(bot.token, channelName)
                .recover { throw NoSuchEntityException() }
                // channel must be exist at this point
                .thenCompose { getChannelId(channelName) }
                .thenCompose { channelId -> removeChannelFromBot(channelName, channelId) }
                .thenCompose { removeBotFromChannel(channelName) }
                .thenCompose { courseApp.removeListener(bot.token, lastSeenCallback) } //TODO: remove listener from storage
    }

    override fun channels(): CompletableFuture<List<String>> {
        return botTreeWrapper.treeGet(Bot.LIST_BOT_CHANNELS, bot.name)
    }

    private fun restartMetadata(label: String, key: String): CompletableFuture<Unit> {
        return courseBotApi.findMetadata(label, key)
                .thenCompose {
                    if (it == null) courseBotApi.createMetadata(label, key, 0L)
                    else courseBotApi.updateMetadata(label, key, 0L)
                }
    }

    private fun combineRegexMediaType(regex: String?, mediaType: MediaType?): String {
        return Pair(mediaType?.ordinal?.toLong(), regex).pairToString()
    }

    private fun addBotPrefixToLabel(botName: String, label: String) = "$botName,$label"

    private fun createNamedRegexMedia(name: String, label: String) = "$name,$label"
    private fun extractLabelFromNamedLabel(namedLabel: String) = namedLabel.split(',')[0]
    private fun extractNameFromNamedLabel(namedLabel: String) = namedLabel.split(',', limit = 1)[1]

    // manage list of pairs (mediaType, regex)
    // in begin - add the pair to the list, and add metadata with counter = 0
    // in update - find the pair in the list, if exist - get&update metadata to counter++
    // in init statistics - iterate over the list, get&update metadata to counter = 0, clear list
    // TODO: fix
    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        if (regex == null && mediaType == null) ImmediateFuture { throw IllegalArgumentException() } //TODO: ask matan if throw without future
        val combinedRegexMedia = combineRegexMediaType(regex, mediaType)
        val key = "${bot.name},$combinedRegexMedia"
        val countCallback: ListenerCallback = { source: String, message: Message ->
            if (shouldBeCountMessage(regex, mediaType, source, message)) {
                courseBotApi.findMetadata(botsStorageName, key)
                        .thenCompose { counter -> courseBotApi.updateMetadata(botsStorageName, key, counter!! + 1L) }
                        .thenCompose {
                            val channelName = source.channelName
                            val namedRegexMedia = createNamedRegexMedia(channelName, combinedRegexMedia)
                            val namedKey = "${bot.name},$namedRegexMedia"
                            courseBotApi.findMetadata(botsStorageName, namedKey)
                                    .thenCompose { channelCounter -> courseBotApi.updateMetadata(botsStorageName, namedKey, channelCounter!! + 1L) }
                        }
            } else ImmediateFuture { }
        }
        return botTreeWrapper.treeGet(Bot.LIST_BOT_CHANNELS, bot.name)
                .thenCompose {
                    it.mapComposeList { channelName ->
                        val namedRegexMedia = createNamedRegexMedia(channelName, combinedRegexMedia)
                        val namedKey = "${bot.name},$namedRegexMedia"
                        restartMetadata(botsStorageName, namedKey)
                    }
                }
                .thenCompose { restartMetadata(botsStorageName, key) }
                .thenCompose { courseApp.addListener(bot.token, countCallback) } //TODO: add listener to storage
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        val combinedRegexMedia = combineRegexMediaType(regex, mediaType)
        val key = "${bot.name},$combinedRegexMedia"
        return if (channel == null) {
            courseBotApi.findMetadata(botsStorageName, key).thenApply { it ?: throw IllegalArgumentException() }
        } else {
            val namedRegexMedia = createNamedRegexMedia(channel, combinedRegexMedia)
            val namedKey = "${bot.name},$namedRegexMedia"
            courseBotApi.findMetadata(botsStorageName, namedKey).thenApply { it ?: throw IllegalArgumentException() }
        }
    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = "$trigger <[()\\d*+/-\\s]+>".toRegex()
        return setCallBackTrigger(BotClient::calculationTrigger, trigger, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val expression = regex.matchEntire(content)!!.groups[1]!!.value
            val solution = Value(expression).resolve()
            val messageFuture = messageFactory.create(MediaType.TEXT, "$solution".toByteArray())
            messageFuture.thenCompose { courseApp.channelSend(bot.token, source.channelName, it) }
        }
    }

    private fun setCallBackTrigger(prop: KMutableProperty1<BotClient, String?>, trigger: String?, r: Regex, action: (source: String, message: Message) -> CompletableFuture<Unit>)
            : CompletableFuture<String?> {
        val prev = prop.get(bot)
        prop.set(bot, trigger)
        val triggerCallback: ListenerCallback = { source: String, message: Message ->
            val content = String(message.contents)
            if (isChannelNameValid(source) && trigger != null && r matches content) action(source, message)
            else ImmediateFuture { }
        }
        return courseApp.addListener(bot.token, triggerCallback).thenApply { prev } //TODO: add listener to storage
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


    private fun Pair<Long?, String?>.pairToString(): String {
        return when {
            first == null && second == null -> ","
            first == null -> ",$second"
            second == null -> "$first,"
            else -> "$first,$second"
        }
    }
    private fun String.stringToPair(): Pair<Long?, String?>{
        val values = this.split(',')
        val first = values[0]
        val second = values[1]
        return when {
            first == "" && second == "" -> Pair(null, null)
            first == "" -> Pair(null, second)
            second == "" -> Pair(first.toLong(), null)
            else -> Pair(first.toLong(), second)
        }
    }
}