package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.github.vjames19.futures.jdk8.recover
import java.util.concurrent.CompletableFuture
import javax.inject.Inject


typealias  BotId = Long

class CourseBotsImpl @Inject constructor(botsSecureStorage: SecureStorage
                                         , private val courseApp: CourseApp
                                         //, private val database: DataBase<String, Long>
                                         , private val messageFactory: MessageFactory
) : CourseBots {


    //private val bots = AVLTree<BotId, Bot>(botsSecureStorage, "bots")

    init {

    }

    companion object {
        private const val annaCounterKey = "botsCounter"
        private const val botDefaultName = "Anna"
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {

        return generateBotId().thenApply { chooseBotName(name, it) }
                .thenCompose { (id, botName) -> loginBotFuture(botName, id) }
                .thenApply { (token, id, botName) ->
                    val bot = Bot(id, token, botName)
                    addBot(id, bot)
                    CourseBotImpl(bot, courseApp, messageFactory)
                }

    }

    private fun addBot(id: Long, bot: Bot): Bot {
       // bots.add(id, bot)
        return bot
    }

    private fun getBot(id: Long):Bot = TODO()//(bots.get(id) as Bot)


    private fun chooseBotName(name: String?, it: BotId): Pair<BotId, String> =
            if (name == null) Pair(it, "$botDefaultName$it") else Pair(it, name)


    private fun loginBotFuture(botName: String, id: Long): CompletableFuture<Triple<String, Long, String>>? =
            courseApp.login(botName, "")
                    .recover {
                        if (it is UserAlreadyLoggedInException)
                            getBot(id).token
                        else throw it
                    }.thenApply { token -> Triple(token, id, botName) }


    private fun generateBotId(): CompletableFuture<BotId> {
        TODO()
        /*return database.read(annaCounterKey).thenCompose { currentCounter ->
            if (currentCounter == null)
                database.write(annaCounterKey, 0L).thenApply { 0L }
            else
                database.write(annaCounterKey, currentCounter + 1L).thenApply { currentCounter + 1L }
        }*/
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        TODO()
        /*return ImmediateFuture {
            listOf<String>()
           *//* bots.asSequence()
                    .filter { (_, bot) -> bot.channels.contains(channel) }
                    .map { (_, bot) -> bot.name }
                    .toList()*//*
        }*/
    }
}