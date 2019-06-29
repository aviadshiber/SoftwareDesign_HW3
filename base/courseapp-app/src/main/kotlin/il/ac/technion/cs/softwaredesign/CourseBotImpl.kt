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
        private const val lastSeenTreeType="lastSeenTreeType"
        private const val keySeperator = "~"
        fun combineArgsToString(vararg values: Any?): String =
                values.joinToString(separator = keySeperator) { it?.toString() ?: "" }
    }

    private val channelTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "channel_")
    private val botTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")
    private val lastSeenUserTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")


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
        return courseApp.channelJoin(bot.token, channelName)
                .recover { throw UserNotAuthorizedException() }
                .thenCompose { createChannelIfNotExist(channelName) }
                .thenCompose { channelId -> addChannelToBot(channelName, channelId) }
                .thenCompose { addBotToChannel(channelName) }
                .thenCompose { courseApp.addListener(bot.token, buildLastSeenMsgCallback(channelName)) }
                .thenCompose { courseApp.addListener(bot.token, buildMostActiveUserCallback(channelName)) }
    }

    fun loadAllBotListeners(): CompletableFuture<Unit> {
        val primitiveCallbacks = getChannelNames()
                .thenCompose { channelNames ->
                    channelNames.mapComposeList { channelName ->
                        courseApp.addListener(bot.token, buildLastSeenMsgCallback(channelName)) // TODO: fix LastSeen callbacks
                                .thenCompose { courseApp.addListener(bot.token, buildMostActiveUserCallback(channelName)) }
                    }
                }
        val messagesCallbacks = courseBotApi.treeGet(msgCounterTreeType, bot.name)
                .thenCompose<Unit> { keys ->
                    keys.filter { key ->
                        MessageCounterTreeKey.buildFromString(key).botName == bot.name
                    }.mapComposeList { key ->
                        val extractedKey = MessageCounterTreeKey.buildFromString(key)
                        courseApp.addListener(bot.token, buildBeginCountCallback(extractedKey.botName, extractedKey.channelName, extractedKey.regex, extractedKey.mediaType))
                    }
                }
        val surveys = courseBotApi.treeGet(surveyTreeType, bot.name)
                .thenCompose {
                    it.mapComposeList { key ->
                        val sid = SurveiesTreeKey.buildFromString(key).surveyId
                        val survey = SurveyClient(sid.toLong(), bot.name, courseBotApi)
                        courseApp.addListener(bot.token, buildSurveyCallback(survey.channel, survey))
                    }
                }.thenDispose()

        //this is should be enough to load from storage the triggers (using botClient will do the side effect
        // that was the point of Bot from the first place)
        val calculationTriggerCallback = setCalculationTrigger(bot.calculationTrigger).thenDispose()
        val tipTriggerCallback = setTipTrigger(bot.tipTrigger).thenDispose()
        return Future.allAsList(listOf(primitiveCallbacks, messagesCallbacks, calculationTriggerCallback, tipTriggerCallback, surveys)).thenDispose()
    }

    private fun buildMostActiveUserCallback(channelName: String): ListenerCallback {
        return { source: String, _: Message ->
            ImmediateFuture {
                if (isChannelNameValid(source) && source.channelName == channelName) {
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
            if (isChannelNameValid(source) && source.channelName == channelName) {
                val key = GenericKeyPair(0L, source.sender)
                lastSeenUserTreeWrapper.treeSearch(lastSeenTreeType, bot.name, key).thenCompose { timeString ->
                    updateLastSeenForNewMessageFuture(key, timeString, message)
                }
            }else{
                ImmediateFuture {  }
            }
        }
    }

    private fun updateLastSeenForNewMessageFuture(key: GenericKeyPair<Long, String>, timeString: String?, message: Message): CompletableFuture<Unit> {
        val messageCreatedTimeInString= message.created.convertToString()
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
                .mapError { throw NoSuchEntityException() }
                // channel must be exist at this point
                .thenCompose { cleanAllBotStatisticsOnChannel(channelName) }
                .thenCompose { getChannelId(channelName) }
                .thenCompose { channelId -> removeChannelFromBot(channelName, channelId) }
                .thenCompose { removeBotFromChannel(channelName) }
                .thenCompose { courseApp.removeListener(bot.token, buildLastSeenMsgCallback(channelName)) }
                .thenCompose { courseApp.removeListener(bot.token, buildMostActiveUserCallback(channelName)) }
    }

    private fun cleanAllBotStatisticsOnChannel(channelName: String): CompletableFuture<Unit> {
        return invalidateCounter(channelName, msgCounterTreeType, bot.name)
                .thenCompose { CashBalance(channelName, bot.name, courseBotApi).cleanData() }
                .thenCompose {
                    courseBotApi.treeGet(surveyTreeType, bot.name)
                            .thenCompose {
                                it.mapComposeList { sid ->
                                    SurveyClient.initializeSurveyStatisticsInChannel(sid.toLong(), bot.name, channelName, courseBotApi)
                                }
                            }
                    // .thenCompose { courseBotApi.treeClean(surveyTreeType, bot.name) } //we should never delete the surveys even when bot leave a channel
                }
    }

    private fun invalidateCounter(channelName: String?, treeType: String, name: String): CompletableFuture<Unit> {
        return courseBotApi.treeToSequence(treeType, name)
                .thenApply { seq ->
                    seq.filter { (genericKey, _) ->
                            botAndChannelMatchKeyPair(treeType, genericKey, channelName) }
                        .map {
                            (genericKey, _) ->
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
                .thenApply { buildBeginCountCallback(bot.name, channel, regex, mediaType) }
                .thenCompose { countCallback -> courseApp.addListener(bot.token, countCallback) }
    }

    /**
     * the method increase the counter with [counterId].
     * the counter id must exist inorder to use this method!!
     */
    private fun incCounterValue(counterId: String): CompletableFuture<Counter> {
        return courseBotApi.findCounter(counterId)
                .thenCompose {
                    counter ->
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
        return setCallBackForTrigger(Bot::calculationTrigger, trigger, regex) { source: String, message: Message ->
            val content = String(message.contents)
            val (expression) = regex.find(content)!!.destructured
            val solution = Value(expression).resolve().toInt()
            val messageFuture = messageFactory.create(MediaType.TEXT, "$solution".toByteArray(Charsets.UTF_8))
            messageFuture.thenCompose { courseApp.channelSend(bot.token, source.channelName, it) }
        }
    }

    private fun calculationTriggerRegex(trigger: String?) = """$trigger ([()\s\d*+-/]+)""".toRegex()

    private fun setCallBackForTrigger(prop: KMutableProperty1<Bot, String?>, trigger: String?, r: Regex,
                                      action: (source: String, message: Message) -> CompletableFuture<Unit>)
            : CompletableFuture<String?> {
        val prev = prop.get(bot)
        prop.set(bot, trigger)
        val triggerCallback: ListenerCallback = { source: String, message: Message ->
            val content = String(message.contents)
            if (isChannelNameValid(source) && trigger != null && r matches content) action(source, message)
            else ImmediateFuture { }
        }
        return courseApp.addListener(bot.token, triggerCallback).thenApply { prev }
    }

    override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
        val regex = tippingRegex(trigger) //$trigger $number $user
        return setCallBackForTrigger(Bot::tipTrigger, trigger, regex) { source: String, message: Message ->
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
        return lastSeenUserTreeWrapper.treeSearch(lastSeenTreeType,bot.name,GenericKeyPair(0L,user))
                .thenApply {timeString-> timeString?.convertToLocalDateTime() }
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
                .thenCompose { survey -> courseApp.addListener(bot.token, buildSurveyCallback(channel, survey)).thenApply { survey } }
                .thenCompose { survey -> messageFactory.create(MediaType.TEXT, question.toByteArray()).thenApply { Pair(survey, it) } }
                .thenCompose { (survey, m) -> courseApp.channelSend(bot.token, channel, m).thenApply { survey.id } }
                .thenCompose { surveyId ->
                    val botNameSurveyId = SurveiesTreeKey(bot.name, surveyId).build()
                    courseBotApi.treeInsert(surveyTreeType, bot.name, GenericKeyPair(0L, botNameSurveyId)).thenApply { surveyId }
                }
    }


    // botname_channel_surveyId
    private fun buildSurveyCallback(channel: String, surveyClient: SurveyClient): ListenerCallback {
        return { source: String, message: Message ->
            isValidRegistration(bot.name, source, channel).thenCompose { valid ->
                if (valid) {
                    val content = String(message.contents)
                    val sender = source.sender
                    surveyClient.getAnswers().thenCompose { answers ->
                        answers.filter { answer -> content.contentEquals(answer) }
                                .mapComposeList { answer -> surveyClient.voteForAnswer(answer, sender) }
                    }
                } else ImmediateFuture { }
            }
        }
    }

    override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
        return courseBotApi.findSurvey(identifier)
                .thenApply {
                    if (it == null || it.botName != bot.name) throw NoSuchEntityException()
                    else SurveyClient(identifier.toLong(), bot.name, courseBotApi).getVoteCounters()
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
                    .exceptionally { if (it !is InvalidTokenException) throw NoSuchEntityException() else throw it }

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
                val splitedString = s.split(keySeperator, limit = 5)
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
                val splitedString = s.split(keySeperator, limit = 3)
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
                val splitedString = s.split(keySeperator, limit = 2)
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