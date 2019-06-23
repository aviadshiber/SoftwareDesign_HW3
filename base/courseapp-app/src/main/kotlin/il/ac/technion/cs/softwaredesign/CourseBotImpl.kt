package il.ac.technion.cs.softwaredesign


import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.expressionsolver.Value
import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Bot
import il.ac.technion.cs.softwaredesign.lib.api.model.BotsMetadata
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.Counter
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.math.max
import kotlin.reflect.KMutableProperty1


class CourseBotImpl(private val bot: BotClient, private val courseApp: CourseApp, private val messageFactory: MessageFactory,
                    private val courseBotApi: CourseBotApi
) : CourseBot {


    internal companion object {
        val channelNameRule = "#[#_A-Za-z0-9]*".toRegex()
        private const val botsMetadataName = "allBots"
        private const val botsStorageName = "botsStorage"
        const val KEY_LAST_CHANNEL_ID = "lastChannelId"
        private const val msgCounterTreeType = "msgCounter"
        private const val userMsgCounterTreeType = "userMsgCounter"
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
                    } else ImmediateFuture { it.channelId }
                }
    }

    private fun getChannelId(channelName: String) = courseBotApi.findChannel(channelName).thenApply { it!!.channelId }

    private fun generateChannelId(): CompletableFuture<Long> {
        return courseBotApi.findCounter(KEY_LAST_CHANNEL_ID)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createCounter(KEY_LAST_CHANNEL_ID).thenApply { 0L }
                    else
                        courseBotApi.updateCounter(KEY_LAST_CHANNEL_ID, currId.value + 1L)
                                .thenApply { currId.value + 1L }
                }
    }

    private fun generateSurveyId(): CompletableFuture<Long> {
        return courseBotApi.findCounter(KEY_LAST_CHANNEL_ID)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createCounter(KEY_LAST_CHANNEL_ID).thenApply { 0L }
                    else
                        courseBotApi.updateCounter(KEY_LAST_CHANNEL_ID, currId.value + 1L)
                                .thenApply { currId.value + 1L }
                }
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
                .thenCompose { courseApp.addListener(bot.token, buildLastSeenMsgCallback(channelName)) } //TODO: add listener to storage
                .thenCompose { courseApp.addListener(bot.token, buildMostActiveUserCallback(channelName)) } //TODO: add listener to storage
    }

    private fun buildMostActiveUserCallback(channelName: String): ListenerCallback {
        return { source: String, _: Message ->
            ImmediateFuture {
                if (isChannelNameValid(source) && source.channelName == channelName) {
                    val sender = source.sender
                    if (sender.isEmpty()) ImmediateFuture { Unit }
                    else {
                        val key = combineArgsToString(bot.name, channelName, sender)
                        courseBotApi.treeContains(userMsgCounterTreeType, bot.name, GenericKeyPair(0L, key))
                                .thenCompose {
                                    courseBotApi.treeInsert(userMsgCounterTreeType, bot.name, GenericKeyPair(0L, key))
                                }
                                .thenCompose {
                                    incCounterValue(key)
                                }
                                .thenApply { userCounter ->
                                    val currMax = bot.mostActiveUserCount ?: -1L
                                    val maxCount = max(currMax, userCounter.value)
                                    if (maxCount > currMax) {
                                        bot.mostActiveUser = sender
                                        bot.mostActiveUserCount = maxCount
                                    }
                                }
                    }
                }
            }
        }
    }

    private fun buildLastSeenMsgCallback(channelName: String): ListenerCallback {
        return { source: String, message: Message ->
            ImmediateFuture {
                if (isChannelNameValid(source) && source.channelName == channelName && isNewMessageByCreationTime(message))
                    bot.lastSeenMessageTime = message.created
            }
        }
    }

    private fun buildBeginCountCallback(botName: String, channelName: String?, regex: String?, mediaType: MediaType?): ListenerCallback {
        return { source: String, message: Message ->
            isValidRegistration(botName, source, channelName).thenCompose {
                if (!it || !shouldBeCountMessage(regex, mediaType, source, message)) ImmediateFuture { }
                else {
                    val channelRegexMediaCounter = combineArgsToString(botName, source.channelName, regex, mediaType)
                    incCounterValue(channelRegexMediaCounter).thenApply {}
                }
            }
        }
    }

    private fun beginCountCountersInit(botName: String, channelName: String?, regex: String?, mediaType: MediaType?)
            : CompletableFuture<Unit> {
        val channelRegexMediaCounter = combineArgsToString(botName, channelName, regex, mediaType)
        return restartCounter(channelRegexMediaCounter)
                .thenCompose { courseBotApi.treeInsert(msgCounterTreeType, botName, GenericKeyPair(0L, channelRegexMediaCounter)) }
                .thenApply { }
    }

    private fun isValidRegistration(botName: String, source: String, channelName: String?): CompletableFuture<Boolean> {
        val sourceChannelName = source.channelName
        if (!isChannelNameValid(sourceChannelName) || (channelName != null && sourceChannelName != channelName)) return ImmediateFuture { false }
        return courseBotApi.findChannel(sourceChannelName).thenApply { it!!.channelId }
                .thenCompose { channelId -> botTreeWrapper.treeContains(Bot.LIST_BOT_CHANNELS, botName, GenericKeyPair(channelId, sourceChannelName)) }
    }

    override fun part(channelName: String): CompletableFuture<Unit> {
        //todo: clean statistics (put null in all counters ['begin count'])
        // TOOD: which statistics exactly?
        return courseApp.channelPart(bot.token, channelName)
                .recover { throw NoSuchEntityException() }
                // channel must be exist at this point
                .thenCompose { cleanAllBotStatisticsOnChannel(channelName) }
                .thenCompose { getChannelId(channelName) }
                .thenCompose { channelId -> removeChannelFromBot(channelName, channelId) }
                .thenCompose { removeBotFromChannel(channelName) }
                .thenCompose { courseApp.removeListener(bot.token, buildLastSeenMsgCallback(channelName)) } //TODO: remove listener from storage
                .thenCompose { courseApp.removeListener(bot.token, buildMostActiveUserCallback(channelName)) } //TODO: remove listener from storage
    }

    private fun cleanAllBotStatisticsOnChannel(channelName: String?): CompletableFuture<Unit> {
        return invalidateCounter(channelName, msgCounterTreeType, bot.name)
                .thenCompose { invalidateCounter(channelName, userMsgCounterTreeType, bot.name) }


    }

    private fun invalidateCounter(channelName: String?, treeType: String, name: String): CompletableFuture<Unit> {
        return courseBotApi.treeToSequence(treeType, name).thenApply { seq ->
            seq.filter { (genericKey, _) ->
                channelName == null || extractChannelFromCombinedString(genericKey.getSecond()) == channelName
            }.map { (genericKey, _) ->
                courseBotApi.deleteCounter(genericKey.getSecond()).thenCompose {
                    courseBotApi.treeRemove(treeType, name, genericKey)
                }
            }
        }.thenDispose()
    }

    override fun channels(): CompletableFuture<List<String>> {
        return botTreeWrapper.treeGet(Bot.LIST_BOT_CHANNELS, bot.name)
    }

    private fun restartCounter(key: String): CompletableFuture<Counter> {
        return courseBotApi.findCounter(key)
                .thenCompose {
                    if (it == null) courseBotApi.createCounter(key)
                    else courseBotApi.updateCounter(key, 0L)
                }
    }

//    private fun invalidateCounter(key: String, treeType: String, name: String): CompletableFuture<Unit> {
//        return courseBotApi.findCounter(key)
//                .thenCompose { counter ->
//                    if (counter == null) ImmediateFuture { }
//                    else courseBotApi.deleteCounter(key).thenCompose {
//                                courseBotApi.treeRemove(treeType, name, GenericKeyPair(counter.value, key)) }
//                            .thenDispose()
//                }
//
//    }

    private fun combineArgsToString(vararg values: Any?): String =
            values.joinToString(separator = ",") { it?.toString() ?: "" }

    private fun extractChannelFromCombinedString(s: String): String =
            s.substringAfter(",").substringBefore(",")

    // manage list of pairs (mediaType, regex)
    // in begin - add the pair to the list, and add metadata with counter = 0
    // in update - find the pair in the list, if exist - get&update metadata to counter++
    // in init statistics - iterate over the list, get&update metadata to counter = 0, clear list
    // TODO: fix according documentation
    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        if (regex == null && mediaType == null) throw IllegalArgumentException()
        val countCallback: ListenerCallback = buildBeginCountCallback(bot.name, channel, regex, mediaType)
        return beginCountCountersInit(bot.name, channel, regex, mediaType)
                .thenCompose { courseApp.addListener(bot.token, countCallback) } //TODO: add listener to storage
    }

    /**
     * the method increase the counter with [counterId].
     * the counter id must exist inorder to use this method!!
     */
    private fun incCounterValue(counterId: String): CompletableFuture<Counter> {
        return courseBotApi.findCounter(counterId)
                .thenCompose { counter -> courseBotApi.updateCounter(counterId, counter!!.value + 1L) }
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        val channelRegexMediaCounter = combineArgsToString(bot.name, channel, regex, mediaType)
        return courseBotApi.findCounter(channelRegexMediaCounter).thenApply {
            it?.value ?: throw IllegalArgumentException()
        }

    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = "$trigger <[()\\d*+/-\\s]+>".toRegex()
        return setCallBackForTrigger(BotClient::calculationTrigger, trigger, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val expression = regex.matchEntire(content)!!.groups[1]!!.value
            val solution = Value(expression).resolve()
            val messageFuture = messageFactory.create(MediaType.TEXT, "$solution".toByteArray())
            messageFuture.thenCompose { courseApp.channelSend(bot.token, source.channelName, it) }
        }
    }

    private fun setCallBackForTrigger(prop: KMutableProperty1<BotClient, String?>, trigger: String?, r: Regex,
                                      action: (source: String, message: Message) -> CompletableFuture<Unit>)
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
        val regex = "$trigger <[\\d]+> <.*>".toRegex() //$trigger $number $user
        return setCallBackForTrigger(BotClient::tipTrigger, trigger, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val number = regex.matchEntire(content)!!.groups[1]!!.value
            val destUserName = regex.matchEntire(content)!!.groups[2]!!.value
            val channelName = source.channelName
            courseApp.isUserInChannel(bot.token, channelName, destUserName).thenCompose { isDestInChannel ->
                if (isDestInChannel == true) {
                    val cashBalance = CashBalance(channelName, bot.name)
                    cashBalance.transfer(source.sender, destUserName, number.toLong())
                } else {
                    ImmediateFuture { }
                }
            }
        }
    }

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        return ImmediateFuture { bot.lastSeenMessageTime }
    }

    // TODO: what if there are more then one user
    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return validateBotInChannel(channel).thenApply { bot.mostActiveUser }
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        return validateBotInChannel(channel)
                .thenCompose { CashBalance(channel, bot.name).getTop() }
    }


    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        return validateBotInChannel(channel)
                .thenCompose {
                    TODO()
                }
    }

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val String.channelName: String
        get() {
            return this.substringBefore("@", "")
        }
    private val String.sender: String
        get() {
            return this.substringAfter("@", "")
        }

    private fun validateBotInChannel(channel: String) =
            courseApp.isUserInChannel(bot.token, channel, bot.name)
                    .recover { if (it !is InvalidTokenException) throw NoSuchEntityException() else throw it }

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
}