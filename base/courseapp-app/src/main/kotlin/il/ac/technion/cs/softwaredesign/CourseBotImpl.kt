package il.ac.technion.cs.softwaredesign


import com.sun.javaws.exceptions.InvalidArgumentException
import il.ac.technion.cs.softwaredesign.exceptions.InvalidTokenException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.expressionsolver.Value
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.Counter
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.mapComposeList
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.models.BotModel
import il.ac.technion.cs.softwaredesign.models.BotsModel
import il.ac.technion.cs.softwaredesign.services.*
import il.ac.technion.cs.softwaredesign.trees.TreeWrapper
import il.ac.technion.cs.softwaredesign.utils.convertToLocalDateTime
import il.ac.technion.cs.softwaredesign.utils.convertToString
import io.github.vjames19.futures.jdk8.Future
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.mapError
import io.github.vjames19.futures.jdk8.recover
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KMutableProperty1


class CourseBotImpl(private val bot: Bot, private val courseApp: CourseApp, private val messageFactory: MessageFactory,
                    private val courseBotApi: CourseBotApi
) : CourseBot {


    internal companion object {
        val channelNameRule = "#[#_A-Za-z0-9]*".toRegex()
        private const val botsMetadataName = "allBots"
        const val KEY_LAST_CHANNEL_ID = "lastChannelId"
        const val KEY_LAST_SURVEY_ID = "lastChannelId"
        private const val msgCounterTreeType = "msgCounter"
        private const val surveyTreeType = "surveyTreeType"
        private const val lastSeenTreeType = "lastSeenTreeType"
        private const val lastSeenCallbackPrefix = "lastSeenMessage"
        private const val mostActiveCallbackPrefix = "mostActive"
        private const val surveyCallbackPrefix = "surveyCallbacks"
        private const val countCallbackPrefix = "countCallbacks"
        private const val tipTriggerCallbackKey = "tipTriggerCallbackKey"
        private const val calculatorTriggerCallbackKey = "calcTriggerCallback"

        private const val keySeparator = "~"
        fun combineArgsToString(vararg values: Any?): String =
                values.joinToString(separator = keySeparator) { it?.toString() ?: "" }
    }

    private val channelTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "channel_")
    private val botTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")
    private val lastSeenUserTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")

    private val callbacksMap: MutableMap<String, ConcurrentHashMap<ListenerCallback, ListenerCallback>> = ConcurrentHashMap()


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
                                    channelTreeWrapper.treeInsert(BotsModel.ALL_CHANNELS, botsMetadataName, GenericKeyPair(0L, channelName)).thenApply { id }
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
        return courseBotApi.findCounter(KEY_LAST_SURVEY_ID)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createCounter(KEY_LAST_SURVEY_ID).thenApply { 0L }
                    else
                        courseBotApi.updateCounter(KEY_LAST_SURVEY_ID, currId.value + 1L)
                                .thenApply { currId.value + 1L }
                }
    }

    // insert pair of (id, name) to keep the list sorted by generation time
    private fun addBotToChannel(channelName: String) =
            channelTreeWrapper.treeInsert(Channel.LIST_BOTS, channelName, GenericKeyPair(bot.id, bot.name))

    private fun removeBotFromChannel(channelName: String) =
            channelTreeWrapper.treeRemove(Channel.LIST_BOTS, channelName, GenericKeyPair(bot.id, bot.name))

    private fun getChannelNames() = botTreeWrapper.treeGet(BotModel.LIST_BOT_CHANNELS, bot.name)

    private fun addChannelToBot(channelName: String, channelId: Long) =
            botTreeWrapper.treeInsert(BotModel.LIST_BOT_CHANNELS, bot.name, GenericKeyPair(channelId, channelName))

    private fun removeChannelFromBot(channelName: String, channelId: Long) =
            botTreeWrapper.treeRemove(BotModel.LIST_BOT_CHANNELS, bot.name, GenericKeyPair(channelId, channelName))

    private fun isChannelNameValid(s: String) = channelNameRule matches s.channelName

    private fun isNewMessageByCreationTime(message: Message, time: LocalDateTime) = message.created > time

    override fun join(channelName: String): CompletableFuture<Unit> {
        return courseApp.isUserInChannel(bot.token, channelName, bot.name)
                .recover { e -> false }
                .thenCompose { isUserInChannel ->
                    if (isUserInChannel == true) ImmediateFuture { }
                    else courseApp.channelJoin(bot.token, channelName)
                            .recover { throw UserNotAuthorizedException() }
                            .thenCompose { createChannelIfNotExist(channelName) }
                            .thenCompose { channelId -> addChannelToBot(channelName, channelId) }
                            .thenCompose { addBotToChannel(channelName) }
                            .thenCompose { addChannelListener(lastSeenCallbackPrefix, channelName, buildLastSeenMsgCallback(channelName)) }
                            .thenCompose { addChannelListener(mostActiveCallbackPrefix, channelName, buildMostActiveUserCallback(channelName)) }
                            .thenCompose { restoreChannelListeners(countCallbackPrefix, channelName) }
                            .thenCompose { restoreChannelListeners(surveyCallbackPrefix, channelName) }

                }

    }



    private fun restoreChannelListeners(keyPrefix: String, channelName: String?): CompletableFuture<Unit> {
        val key = combineArgsToString(keyPrefix, channelName)
        return (getCallbacks(key)?.mapComposeList { callback -> addChannelListener(keyPrefix, channelName, callback) })
                ?: ImmediateFuture { }

    }

    private fun addChannelListener(keyPrefix: String, channelName: String?, callback: ListenerCallback): CompletableFuture<Unit> {
        val key = combineArgsToString(keyPrefix, channelName)
        putCallback(key, callback)
        return courseApp.addListener(bot.token, callback)
    }

    private fun putCallback(key: String, callback: ListenerCallback) {
        val callbacks = callbacksMap[key] ?: ConcurrentHashMap()
        callbacks[callback] = callback
        callbacksMap[key] = callbacks
    }

    private fun removeChannelListeners(keyPrefix: String, channelName: String): CompletableFuture<Unit> {
        val callbacks = callbacksMap[combineArgsToString(keyPrefix, channelName)]
        return callbacks
                ?.values
                ?.toList()
                ?.mapComposeList { callback -> courseApp.removeListener(bot.token, callback) }
                ?: ImmediateFuture { }
    }

    fun loadAllBotListeners(): CompletableFuture<Unit> {
        val primitiveCallbacks = getChannelNames()
                .thenCompose { channelNames ->
                    channelNames.mapComposeList { channelName ->
                        courseApp.addListener(bot.token, buildLastSeenMsgCallback(channelName))
                                .thenCompose { courseApp.addListener(bot.token, buildMostActiveUserCallback(channelName)) }
                    }
                }
        val messagesCallbacks = courseBotApi.treeGet(msgCounterTreeType, bot.name)
                .thenCompose<Unit> { keys ->
                    keys.filter { key ->
                        MessageCounterTreeKey.buildFromString(key).botName == bot.name
                    }.mapComposeList { key ->
                        val extractedKey = MessageCounterTreeKey.buildFromString(key)
                        val channelName = extractedKey.channelName
                        addChannelListener(countCallbackPrefix, channelName, buildBeginCountCallback(bot.name, channelName, extractedKey.regex, extractedKey.mediaType))
                    }
                }
        val surveys = courseBotApi.treeGet(surveyTreeType, bot.name)
                .thenCompose {
                    it.mapComposeList { key ->
                        val sid = SurveiesTreeKey.buildFromString(key).surveyId
                        val survey = SurveyClient(sid.toLong(), bot.name, courseBotApi)
                        val channel = survey.channel
                        addChannelListener(surveyCallbackPrefix, channel, buildSurveyCallback(channel, survey))
                    }
                }.thenDispose()

        //this is should be enough to load from storage the triggers (using botClient will do the side effect
        // that was the point of Bot from the first place)
        val calculationTriggerCallback = setCalculationTrigger(bot.calculationTrigger).thenDispose()
        val tipTriggerCallback = setTipTrigger(bot.tipTrigger).thenDispose()
        return Future.allAsList(listOf(primitiveCallbacks, messagesCallbacks, calculationTriggerCallback, tipTriggerCallback, surveys)).thenDispose()
    }

    private fun buildMostActiveUserCallback(channelName: String): ListenerCallback {
        return { source: String, m: Message ->
            ImmediateFuture {
                if (isChannelNameValid(source) && m.media == MediaType.TEXT && source.channelName == channelName) {
                    val sender = source.sender
                    if (sender.isEmpty()) ImmediateFuture { Unit }
                    else {
                        MostActiveUsers(channelName, bot.name, courseBotApi).updateSentMsgByUser(sender)
                    }
                }
            }
            // TODO: should we add something here?
        }
    }


    //botNme_channel
    private fun buildLastSeenMsgCallback(channelName: String): ListenerCallback {
        return { source: String, message: Message ->
            if (isChannelNameValid(source) && message.media == MediaType.TEXT && source.channelName == channelName) {
                val key = GenericKeyPair(0L, source.sender)
                lastSeenUserTreeWrapper.treeSearch(lastSeenTreeType, bot.name, key).thenCompose { timeString ->
                    updateLastSeenForNewMessageFuture(key, timeString, message)
                }
            } else {
                ImmediateFuture { }
            }
        }
    }

    private fun updateLastSeenForNewMessageFuture(key: GenericKeyPair<Long, String>, timeString: String?, message: Message): CompletableFuture<Unit> {
        val messageCreatedTimeInString = message.created.convertToString()
        return if (timeString == null) lastSeenUserTreeWrapper.treeInsert(lastSeenTreeType, bot.name, key, messageCreatedTimeInString).thenDispose()
        else {

            if (isNewMessageByCreationTime(message, timeString.convertToLocalDateTime())) {
                lastSeenUserTreeWrapper.treeRemove(lastSeenTreeType, bot.name, key)
                        .thenCompose { lastSeenUserTreeWrapper.treeInsert(lastSeenTreeType, bot.name, key, message.created.convertToString()) }.thenDispose()
            } else {
                ImmediateFuture { }
            }
        }
    }

    private fun buildBeginCountCallback(botName: String, channelName: String?, regex: String?, mediaType: MediaType?): ListenerCallback {
        return { source: String, message: Message ->
            isValidRegistration(botName, source, channelName).thenCompose {
                if (!it || !shouldBeCountMessage(regex, mediaType, source, message)) ImmediateFuture { }
                else {
                    val channelRegexMediaCounter = MessageCounterTreeKey(botName, channelName, regex, mediaType).build()
                    incCounterValue(channelRegexMediaCounter).thenDispose()
                }
            }
        }
    }

    private fun beginCountCountersInit(botName: String, channelName: String?, regex: String?, mediaType: MediaType?)
            : CompletableFuture<Unit> {
        val channelRegexMediaCounter = MessageCounterTreeKey(botName, channelName, regex, mediaType).build()
        return restartCounter(channelRegexMediaCounter)
                .thenCompose { courseBotApi.treeInsert(msgCounterTreeType, botName, GenericKeyPair(0L, channelRegexMediaCounter)) }
                .thenDispose()
    }

    private fun isValidRegistration(botName: String, source: String, channelName: String?): CompletableFuture<Boolean> {
        val sourceChannelName = source.channelName
        if (!isChannelNameValid(source) || (channelName != null && sourceChannelName != channelName)) return ImmediateFuture { false }
        return courseBotApi.findChannel(sourceChannelName).thenApply { it!!.channelId }
                .thenCompose { channelId -> botTreeWrapper.treeContains(BotModel.LIST_BOT_CHANNELS, botName, GenericKeyPair(channelId, sourceChannelName)) }
    }


    override fun part(channelName: String): CompletableFuture<Unit> {
        return courseApp.channelPart(bot.token, channelName)
                .mapError { e: InvalidTokenException -> throw NoSuchEntityException() }
                // channel must be exist at this point
                .thenCompose { cleanAllBotStatisticsOnChannel(channelName) }
                .thenCompose { getChannelId(channelName) }
                .thenCompose { channelId -> removeChannelFromBot(channelName, channelId) }
                .thenCompose { removeBotFromChannel(channelName) }
                .thenCompose { removeChannelListeners(countCallbackPrefix, channelName) }
                .thenCompose { removeChannelListeners(lastSeenCallbackPrefix, channelName) }
                .thenCompose { removeChannelListeners(mostActiveCallbackPrefix, channelName) }
                .thenCompose { removeChannelListeners(surveyCallbackPrefix, channelName) }
    }

    private fun cleanAllBotStatisticsOnChannel(channelName: String): CompletableFuture<Unit> {
        return invalidateCounter(channelName, msgCounterTreeType, bot.name)
                .thenCompose { CashBalance(channelName, bot.name, courseBotApi).cleanData() }
                .thenCompose {
                    courseBotApi.treeGet(surveyTreeType, bot.name)
                            .thenCompose {
                                it.mapComposeList { key ->
                                    val sid = SurveiesTreeKey.buildFromString(key).surveyId
                                    SurveyClient.initializeSurveyStatisticsInChannel(sid.toLong(), bot.name, channelName, courseBotApi)
                                }
                            }
                    // .thenCompose { courseBotApi.treeClean(surveyTreeType, bot.name) } //we should never delete the surveys even when bot leave a channel
                }.thenCompose { MostActiveUsers(channelName, bot.name, courseBotApi).cleanData() }.thenDispose()
    }

    private fun invalidateCounter(channelName: String?, treeType: String, name: String): CompletableFuture<Unit> {
        return courseBotApi.treeToSequence(treeType, name)
                .thenApply { seq ->
                    seq.filter { (genericKey, _) ->
                        botAndChannelMatchKeyPair(treeType, genericKey, channelName)
                    }
                            .map { (genericKey, _) ->
                                restartCounter(genericKey.getSecond()).thenCompose {
                                    courseBotApi.treeRemove(treeType, name, genericKey)
                                }
                            }.toList()
                }.thenDispose()
    }

    private fun botAndChannelMatchKeyPair(treeType: String, genericKey: GenericKeyPair<Long, String>, channelName: String?): Boolean {
        val channel = channelName ?: ""
        //todo change to polymorphic code
        val extractedChannelName =
                when (treeType) {
                    msgCounterTreeType -> MessageCounterTreeKey.buildFromString(genericKey.getSecond()).channelName
                    else -> throw UnsupportedOperationException()
                }
        val extractedBotName =
                when (treeType) {
                    msgCounterTreeType -> MessageCounterTreeKey.buildFromString(genericKey.getSecond()).botName
                    else -> throw UnsupportedOperationException()
                }

        return extractedBotName == bot.name && channel == extractedChannelName
    }

    override fun channels(): CompletableFuture<List<String>> {
        return botTreeWrapper.treeGet(BotModel.LIST_BOT_CHANNELS, bot.name)
    }

    private fun restartCounter(key: String): CompletableFuture<Counter> {
        return courseBotApi.findCounter(key)
                .thenCompose {
                    if (it == null) courseBotApi.createCounter(key)
                    else courseBotApi.updateCounter(key, 0L)
                }
    }

    // manage list of pairs (mediaType, regex)
    // in begin - add the pair to the list, and add metadata with counter = 0
    // in update - find the pair in the list, if exist - get&update metadata to counter++
    // in init statistics - iterate over the list, get&update metadata to counter = 0, clear list
    // TODO: fix according documentation
    override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
        if (regex == null && mediaType == null) throw IllegalArgumentException()
//        val countCallback: ListenerCallback = buildBeginCountCallback(bot.name, channel, regex, mediaType)
        return beginCountCountersInit(bot.name, channel, regex, mediaType)
                .thenCompose { addChannelListener(countCallbackPrefix, channel, buildBeginCountCallback(bot.name, channel, regex, mediaType)) }
    }

    /**
     * the method increase the counter with [counterId].
     * the counter id must exist inorder to use this method!!
     */
    private fun incCounterValue(counterId: String): CompletableFuture<Counter> {
        return courseBotApi.findCounter(counterId)
                .thenCompose { counter ->
                    if (counter == null) courseBotApi.createCounter(counterId)
                            .thenCompose { courseBotApi.updateCounter(counterId, 1L) }
                    else courseBotApi.updateCounter(counterId, counter.value + 1L)
                }
    }

    override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
        val channelRegexMediaCounter = MessageCounterTreeKey(bot.name, channel, regex, mediaType).build()
        return courseBotApi.findCounter(channelRegexMediaCounter).thenApply {
            it?.value ?: throw IllegalArgumentException()
        }

    }

    override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = calculationTriggerRegex(trigger)

        return setCallBackForTrigger(Bot::calculationTrigger, trigger, calculatorTriggerCallbackKey, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val (expression) = regex.find(content)!!.destructured
            try {
                val solution = Value(expression).resolve().toInt()
                val messageFuture = messageFactory.create(MediaType.TEXT, "$solution".toByteArray(Charsets.UTF_8))
                messageFuture.thenCompose { courseApp.channelSend(bot.token, source.channelName, it) }
            } catch (e: Exception) {
                ImmediateFuture { }
            }

        }

    }

    private fun calculationTriggerRegex(trigger: String?) = """$trigger ([()\s\d*+-/]+)""".toRegex()

    private fun setCallBackForTrigger(prop: KMutableProperty1<Bot, String?>, trigger: String?, key: String, r: Regex,
                                      action: (source: String, message: Message) -> CompletableFuture<Unit>)
            : CompletableFuture<String?> {
        val prev = prop.get(bot)
        prop.set(bot, trigger)
        if (prev != null) {
            val prevCallback = getCallback(key)
            if (prevCallback!=null) courseApp.removeListener(bot.token, prevCallback)
            else ImmediateFuture { prev }
        }
        return if (trigger != null) {
            val callback = buildTriggerCallback(trigger, r, action)
            overrideCallback(key, callback)
            courseApp.addListener(bot.token, callback).thenApply { prev }
        } else {
            callbacksMap[key]?.clear()
            ImmediateFuture { prev }
        }
    }

    private fun getCallback(key: String) = getCallbacks(key)?.getOrNull(0)

    private fun getCallbacks(key: String) = callbacksMap[key]?.values?.toList()

    private fun overrideCallback(key: String, callback: ListenerCallback) {
        callbacksMap[key]?.clear()
        putCallback(key, callback)
    }

    private fun buildTriggerCallback(trigger: String?, r: Regex, action: (source: String, message: Message) -> CompletableFuture<Unit>): ListenerCallback {
        return { source: String, message: Message ->
            val content = String(message.contents)
            if (isChannelNameValid(source) && trigger != null && r matches content && message.media == MediaType.TEXT) action(source, message)
            else ImmediateFuture { }
        }
    }


    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = tippingRegex(trigger) //$trigger $number $user
        return setCallBackForTrigger(Bot::tipTrigger, trigger, tipTriggerCallbackKey, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val (number, destUserName) = regex.find(content)!!.destructured
            val channelName = source.channelName
            courseApp.isUserInChannel(bot.token, channelName, destUserName).thenCompose { isDestInChannel ->
                if (isDestInChannel == true) {
                    val cashBalance = CashBalance(channelName, bot.name, courseBotApi)
                    cashBalance.transfer(source.sender, destUserName, number.toLong())
                } else {
                    ImmediateFuture { }
                }
            }
        }
    }

    private fun tippingRegex(trigger: String?) = """$trigger (\d+) (.*)""".toRegex()

    override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
        return lastSeenUserTreeWrapper.treeSearch(lastSeenTreeType, bot.name, GenericKeyPair(0L, user))
                .thenApply { timeString -> timeString?.convertToLocalDateTime() }
    }

    override fun mostActiveUser(channel: String): CompletableFuture<String?> {
        return validateBotInChannel(channel).thenCompose {
            MostActiveUsers(channel, bot.name, courseBotApi).getMostActiveUserInChannel()
        }
    }

    override fun richestUser(channel: String): CompletableFuture<String?> {
        return validateBotInChannel(channel)
                .thenCompose { CashBalance(channel, bot.name, courseBotApi).getTop() }
    }

    override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
        return validateBotInChannel(channel)
                .thenCompose { generateSurveyId() }.thenCompose { id -> SurveyClient(id, bot.name, courseBotApi).createQuestion(question, channel) }
                .thenCompose { survey -> survey.putAnswers(answers).thenApply { survey } }
                .thenCompose { survey ->
                    addChannelListener(surveyCallbackPrefix, channel, buildSurveyCallback(channel, survey)).thenApply { survey }
                }
                .thenCompose { survey -> messageFactory.create(MediaType.TEXT, question.toByteArray()).thenApply { Pair(survey, it) } }
                .thenCompose { (survey, m) -> courseApp.channelSend(bot.token, channel, m).thenApply { survey.id } }
                .thenCompose { surveyId ->
                    val botNameSurveyId = SurveiesTreeKey(bot.name, surveyId).build()
                    courseBotApi.treeInsert(surveyTreeType, bot.name, GenericKeyPair(0L, botNameSurveyId)).thenApply { surveyId }
                }
    }

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        return courseBotApi.findSurvey(identifier)
                .thenApply {
                    if (it == null || it.botName != bot.name) throw NoSuchEntityException()
                    else SurveyClient(identifier.toLong(), bot.name, courseBotApi).getVoteCounters()
                }
    }


    // botname_channel_surveyId
    private fun buildSurveyCallback(channel: String, surveyClient: SurveyClient): ListenerCallback {
        return { source: String, message: Message ->
            isValidRegistration(bot.name, source, channel).thenCompose { valid ->
                if (valid && message.media == MediaType.TEXT) {
                    val content = String(message.contents)
                    val sender = source.sender
                    surveyClient.getAnswers().thenCompose { answers ->
                        answers.filter { answer: String -> content.contentEquals(answer) }
                                .mapComposeList { answer -> surveyClient.voteForAnswer(answer, sender) }
                    }
                } else ImmediateFuture { }
            }
        }
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
                    .mapError { e: UserNotAuthorizedException -> throw NoSuchEntityException() }
                    .thenApply { if (it == false) throw NoSuchEntityException() }

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

    //TODO: generalize this if we have time
    data class MessageCounterTreeKey(val botName: String, val channelName: String?, val regex: String?, val mediaType: MediaType?) {

        fun build() = combineArgsToString(botName, channelName, regex, mediaType)

        companion object {
            fun buildFromString(s: String): MessageCounterTreeKey {
                lateinit var name: String
                lateinit var channel: String
                var r: String? = null
                var m: MediaType? = null
                val splitedString = s.split(keySeparator, limit = 5)
                val e = InvalidArgumentException(arrayOf("The string does not match MessageCounterTreeKey pattern"))
                if (splitedString.size > 4) throw e
                splitedString.forEachIndexed { i, data ->
                    when (i) {
                        0 -> name = data
                        1 -> channel = data
                        2 -> r = data
                        3 -> m = if (data.isNotEmpty()) MediaType.valueOf(data) else null
                        else -> throw e
                    }
                }
                return MessageCounterTreeKey(name, channel, r, m)
            }
        }
    }

    data class MostActiveUserTreeKey(val botName: String, val channelName: String, val sender: String) {

        fun build() = combineArgsToString(botName, channelName, sender)

        companion object {
            fun buildFromString(s: String): MostActiveUserTreeKey {
                lateinit var name: String
                lateinit var channel: String
                lateinit var sen: String
                val splitedString = s.split(keySeparator, limit = 3)
                val e = InvalidArgumentException(arrayOf("The string does not match MostActiveUserTreeKey pattern"))
                if (splitedString.size > 3) throw e
                splitedString.forEachIndexed { i, data ->
                    when (i) {
                        0 -> name = data
                        1 -> channel = data
                        2 -> sen = data
                        else -> throw e
                    }
                }
                return MostActiveUserTreeKey(name, channel, sen)
            }
        }
    }

    data class SurveiesTreeKey(val botName: String, val surveyId: String) {

        fun build() = combineArgsToString(botName, surveyId)

        companion object {
            fun buildFromString(s: String): SurveiesTreeKey {
                lateinit var name: String
                lateinit var sid: String
                val splitedString = s.split(keySeparator, limit = 2)
                val e = InvalidArgumentException(arrayOf("The string does not match SurveiesTreeKey pattern"))
                if (splitedString.size > 2) throw e
                splitedString.forEachIndexed { i, data ->
                    when (i) {
                        0 -> name = data
                        1 -> sid = data
                        else -> throw e
                    }
                }
                return SurveiesTreeKey(name, sid)
            }
        }
    }


}