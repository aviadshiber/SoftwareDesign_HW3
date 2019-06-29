package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.lib.api.model.Channel
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.mapComposeList
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.models.BotsModel
import il.ac.technion.cs.softwaredesign.services.Bot
import il.ac.technion.cs.softwaredesign.services.CourseBotApi
import il.ac.technion.cs.softwaredesign.trees.TreeWrapper
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton


typealias  BotId = Long

@Singleton
class CourseBotsImpl @Inject constructor(private val courseApp: CourseApp,
                                         private val courseBotApi: CourseBotApi,
                                         private val messageFactory: MessageFactory
) : CourseBots {


    private val channelTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "channel_")
    private val botTreeWrapper: TreeWrapper = TreeWrapper(courseBotApi, "bot_")

    companion object {
        private const val botDefaultName = "Anna"
        private const val botsTreeName = "allBots"
        const val KEY_LAST_BOT_ID = "lastBotId"
    }

    override fun prepare(): CompletableFuture<Unit> {
        //TODO: not sure in anything should happen here
        return ImmediateFuture { }
    }

    override fun start(): CompletableFuture<Unit> {
        return botTreeWrapper.treeGet(BotsModel.ALL_BOTS, botsTreeName)
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
                        .thenCompose {
                            if (it == null)
                                courseBotApi.createBot(id, botName, token)
                                        .thenCompose { bot ->
                                            botTreeWrapper.treeInsert(BotsModel.ALL_BOTS, botsTreeName, GenericKeyPair(bot!!.botId, bot.botName)).thenApply { bot }
                                        }
                            else ImmediateFuture { it } }
                }
//                .thenCompose { bot -> botTreeWrapper.treeInsert(BotsModel.ALL_BOTS, botsTreeName, GenericKeyPair(bot!!.botId, bot.botName)).thenApply { bot } }
                .thenApply { bot -> CourseBotImpl(Bot(bot!!.botId, bot.botToken, bot.botName, courseBotApi), courseApp, messageFactory, courseBotApi) }
    }

    private fun generateCourseBotFromBotName(name: String): CompletableFuture<CourseBotImpl> {
        return courseBotApi.findBot(name)
                .thenApply { bot ->
                    CourseBotImpl(Bot(bot!!.botId, bot.botToken, bot.botName, courseBotApi),
                            courseApp, messageFactory, courseBotApi)
                }
    }

    private fun chooseBotName(name: String?, it: BotId): Pair<BotId, String> =
            if (name == null) Pair(it, "$botDefaultName$it") else Pair(it, name)

    private fun loginBotIfNotExistFuture(botName: String, id: Long): CompletableFuture<Triple<String, Long, String>>? =
            courseApp.login(botName, "pwd").exceptionally { e ->
                if (e.cause is UserAlreadyLoggedInException) {
                    courseBotApi.findBot(botName).thenApply { bot -> bot!!.botToken }.join()
                }
                else throw e
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

    override fun bots(channel: String?): CompletableFuture<List<String>> {
            return if (channel == null)
                botTreeWrapper.treeGet(BotsModel.ALL_BOTS, botsTreeName)
            else
                channelTreeWrapper.treeGet(Channel.LIST_BOTS, channel)
    }
}