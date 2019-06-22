package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.api.model.Bot
import il.ac.technion.cs.softwaredesign.lib.api.model.BotsMetadata
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
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
    }

    override fun prepare(): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {
        return generateBotId().thenApply { chooseBotName(name, it) }
                .thenCompose { (id, botName) -> loginBotIfNotExistFuture(botName, id) }
                .thenCompose { (token, id, botName) -> courseBotApi.createBot(botName, token, id) }
//                .thenCompose { bot -> courseBotApi.listInsert(BotsMetadata.ALL_BOTS, botsMetadataName, Pair(bot!!.botId, bot.botName).pairToString()).thenApply { bot } }
                .thenCompose { bot -> botTreeWrapper.treeInsert(BotsMetadata.ALL_BOTS, botsMetadataName, GenericKeyPair(bot!!.botId, bot.botName)).thenApply { bot } }
                .thenApply { bot -> CourseBotImpl(BotClient(bot!!.botId, bot.botToken, bot.botName, courseBotApi), courseApp, messageFactory, courseBotApi) }
    }

    private fun getBot(name: String): CompletableFuture<Bot?> {
        return courseBotApi.findBot(name)
    }

    private fun chooseBotName(name: String?, it: BotId): Pair<BotId, String> =
            if (name == null) Pair(it, "$botDefaultName$it") else Pair(it, name)

    private fun loginBotIfNotExistFuture(botName: String, id: Long): CompletableFuture<Triple<String, Long, String>>? =
            courseApp.login(botName, "").recoverWith {
                if (!(it is UserAlreadyLoggedInException)) throw it
                else courseBotApi.findBot(botName).thenApply { it!!.botToken }
            } .thenApply { token: String -> Triple(token, id, botName) }

    private fun generateBotId(): CompletableFuture<Long> {
        return courseBotApi.findMetadata(BotsMetadata.KEY_LAST_BOT_ID, botsMetadataName)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createMetadata(BotsMetadata.KEY_LAST_BOT_ID, botsMetadataName, 0L).thenApply { 0L }
                    else
                        courseBotApi.updateMetadata(BotsMetadata.KEY_LAST_BOT_ID, botsMetadataName, currId+1L)
                            .thenApply { currId+1L } }
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