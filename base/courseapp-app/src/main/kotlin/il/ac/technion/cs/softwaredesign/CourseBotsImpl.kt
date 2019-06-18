package il.ac.technion.cs.softwaredesign

import PersistentDataStructures.Tree.AVLTree
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class CourseBotsImpl @Inject constructor(botsSecureStorage: SecureStorage, private val courseApp: CourseApp
                                         , private val database: DataBase<String, Long>) : CourseBots {

    val bots = AVLTree<Long, String>(botsSecureStorage, "bots")

    init {

    }

    companion object {
        private val annaCounterKey = "botsCounter"
        private val botDefaultName = "Anna"
    }

    override fun bot(name: String?): CompletableFuture<CourseBot> {

        return generateBotId().thenApply { chooseBotName(name, it) }
                .thenCompose { (id, botName) -> loginBotFuture(botName, id) }
                .thenApply { (token, id, botName) ->
                    bots.add(id, botName)
                    CourseBotImpl(token, id, botName, courseApp)//TODO: change
                }

    }

    private fun chooseBotName(name: String?, it: Long): Pair<Long, String> =
            if (name == null) Pair(it, "$botDefaultName$it") else Pair(it, name)

    private fun loginBotFuture(botName: String, id: Long): CompletableFuture<Triple<String, Long, String>>? =
            courseApp.login(botName, "").thenApply { token -> Triple(token, id, botName) }


    private fun generateBotId(): CompletableFuture<Long> {
        return database.read(annaCounterKey).thenCompose { currentCounter ->
            if (currentCounter == null)
                database.write(annaCounterKey, 0L).thenApply { 0L }
            else
                database.write(annaCounterKey, currentCounter + 1L).thenApply { currentCounter + 1L }
        }
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        //bots.asSequence()
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}