package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Bot
import il.ac.technion.cs.softwaredesign.lib.api.model.BotsMetadata
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.mapComposeList
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import io.github.vjames19.futures.jdk8.ImmediateFuture
import io.github.vjames19.futures.jdk8.recoverWith
import java.util.concurrent.CompletableFuture
import javax.inject.Inject


typealias  BotId = Long

class CourseBotsImpl @Inject constructor(private val courseApp: CourseApp,
                                         private val courseBotApi: CourseBotApi, // TODO: should be a singleton
                                         private val messageFactory: MessageFactory
) : CourseBots {

    init {
    }

    private val channelTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "channel_")
    private val botTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")

    companion object {
        private const val botDefaultName = "Anna"
        private const val botsMetadataName = "allBots"
        const val KEY_LAST_BOT_ID = "lastBotId"
    }

    override fun prepare(): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(): CompletableFuture<Unit> {
        return botTreeWrapper.treeGet(BotsMetadata.ALL_BOTS, botsMetadataName)
                .thenCompose { it.mapComposeList { botName ->
                        generateCourseBotFromBotName(botName)
                                .thenCompose { botImpl -> botImpl.loadAllBotListeners() }
                    }
                }
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {
        return generateBotId().thenApply { chooseBotName(name, it) }
                .thenCompose { (id, botName) -> loginBotIfNotExistFuture(botName, id) }
                .thenCompose { (token, id, botName) -> courseBotApi.findBot(botName)
                        .thenCompose { if(it==null) courseBotApi.createBot(botName, token, id) else ImmediateFuture { it }}}
//                .thenCompose { bot -> courseBotApi.listInsert(BotsMetadata.ALL_BOTS, botsMetadataName, Pair(bot!!.botId, bot.botName).pairToString()).thenApply { bot } }
                .thenCompose { bot -> botTreeWrapper.treeInsert(BotsMetadata.ALL_BOTS, botsMetadataName, GenericKeyPair(bot!!.botId, bot.botName)).thenApply { bot } }
                .thenApply { bot -> CourseBotImpl(BotClient(bot!!.botId, bot.botToken, bot.botName, courseBotApi), courseApp, messageFactory, courseBotApi) }
    }

    private fun generateCourseBotFromBotName(name: String): CompletableFuture<CourseBotImpl> {
        return courseBotApi.findBot(name)
                .thenApply { bot ->
                    CourseBotImpl(BotClient(bot!!.botId, bot.botToken, bot.botName, courseBotApi),
                            courseApp, messageFactory, courseBotApi)
                }
    }

    private fun chooseBotName(name: String?, it: BotId): Pair<BotId, String> =
            if (name == null) Pair(it, "$botDefaultName$it") else Pair(it, name)

    private fun loginBotIfNotExistFuture(botName: String, id: Long): CompletableFuture<Triple<String, Long, String>>? =
            courseApp.login(botName, "").recoverWith {
                if (it !is UserAlreadyLoggedInException) throw it
                else courseBotApi.findBot(botName).thenApply { it!!.botToken }
            } .thenApply { token: String -> Triple(token, id, botName) }

    private fun generateBotId(): CompletableFuture<Long> {
        return courseBotApi.findCounter(KEY_LAST_BOT_ID)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createCounter(KEY_LAST_BOT_ID).thenApply { 0L }
                    else
                        courseBotApi.updateCounter(KEY_LAST_BOT_ID, currId.value + 1L).thenApply { it.value }
                }
    }

    // TODO: what if channel does not exists?
    // TODO: check what happened if list is empty (null?)
    override fun bots(channel: String?): CompletableFuture<List<String>> {
            return if (channel == null)
                botTreeWrapper.treeGet(BotsMetadata.ALL_BOTS, botsMetadataName)
            else
                channelTreeWrapper.treeGet(Channel.LIST_BOTS, channel)
    }
}