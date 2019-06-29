package il.ac.technion.cs.softwaredesign.services

import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.trees.TreeWrapper
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture

class MostActiveUsers(private val channelName: String, private val botName: String, courseBotApi: CourseBotApi){
    // type = channelName, name = botName, keys: genericKey(0L, username), value: counter
    private val userNamesToMsgCounters = TreeWrapper(courseBotApi, "MAUsers__")

    // type = channelName, name = botName, keys: genericKey(msgCounter, username)
    private val userMsgCountersTree = TreeWrapper(courseBotApi, "MAUsersCounters__")

    fun updateSentMsgByUser(userName: String): CompletableFuture<Unit> {
        return incUserCounter(userName)
    }

    fun getMostActiveUserInChannel(): CompletableFuture<String?> {
        return getTop()
    }

    private fun incUserCounter(username: String): CompletableFuture<Unit> {
        return userNamesToMsgCounters.treeSearch(channelName, botName, GenericKeyPair(0L, username))
                .thenCompose { value ->
                    if (value == null) {
                        userNamesToMsgCounters.treeInsert(channelName, botName, GenericKeyPair(0L, username), value = 1L.toString())
                        userMsgCountersTree.treeInsert(channelName, botName, GenericKeyPair(1L, username))
                    } else {
                        userNamesToMsgCounters.treeRemove(channelName, botName, GenericKeyPair(0L, username))
                        userNamesToMsgCounters.treeInsert(channelName, botName, GenericKeyPair(0L, username), value = (value.toLong() + 1L).toString())

                        userMsgCountersTree.treeRemove(channelName, botName, GenericKeyPair(value.toLong(), username))
                        userMsgCountersTree.treeInsert(channelName, botName, GenericKeyPair(value.toLong() + 1L, username))
                    }
                }.thenApply {  }
    }

    private fun removeUser(username: String): CompletableFuture<Unit> {
        return userNamesToMsgCounters.treeSearch(channelName, botName, GenericKeyPair(0L, username))
                .thenCompose<Boolean> { value ->
                    if (value == null) { ImmediateFuture { true }
                    } else {
                        userNamesToMsgCounters.treeRemove(channelName, botName, GenericKeyPair(0L, username))
                        userMsgCountersTree.treeRemove(channelName, botName, GenericKeyPair(value.toLong(), username))
                    }
                }.thenApply {  }
    }


    private fun getTop(): CompletableFuture<String?> {
        return getMaxOrNull()
    }

    private fun getMaxOrNull(): CompletableFuture<String?> {
        return userMsgCountersTree.treeGetMax(channelName, botName)
                .thenCompose<String?> { keyPairValue ->
                    if (keyPairValue == null) ImmediateFuture { null }
                    else {
                        val keyPair = keyPairValue.first
                        val counter = keyPair.getFirst()
                        userMsgCountersTree.treeRemove(channelName, botName, keyPair)
                                .thenCompose { userMsgCountersTree.treeGetMax(channelName, botName) }
                                .thenApply { secKeyPairValue ->
                                    if (secKeyPairValue == null) true
                                    else {
                                        val secKeyPair = secKeyPairValue.first
                                        val secCounter = secKeyPair.getFirst()
                                        counter != secCounter
                                    }
                                }
                                .thenCompose { res -> userMsgCountersTree.treeInsert(channelName, botName, keyPair).thenApply { res } }
                                .thenApply { if (it) keyPair.getSecond() else null }
                    }
                }
    }

}