package il.ac.technion.cs.softwaredesign.services

import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.trees.TreeWrapper
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture

class CashBalance constructor(private val channelName: String, private val botName: String, courseBotApi: CourseBotApi) {

    // type = channelName, name = botName, keys: genericKey(money, username)
    val cashTreeWrapper = TreeWrapper(courseBotApi, "cash__")

    // type = channelName, name = botName, keys: genericKey(0L, username), values: money
    val userMoneyTreeWrapper = TreeWrapper(courseBotApi, "userMoney__")

    /**
     * TODO: this function assumes sec & dest exists in courseApp
     * transfer [money] from [srcUser] to [destUser] or do nothing if [srcUser] has less than [money]
     * @param srcUser String
     * @param destUser String
     * @param money Long
     * @return CompletableFuture<Unit>
     */
    fun transfer(srcUser: String, destUser: String, money: Long): CompletableFuture<Unit> {
        return getBalance(srcUser)
                .thenCompose { srcMoney -> getBalance(destUser).thenApply { Pair(srcMoney, it) } }
                .thenCompose { (srcMoney, destMoney) ->
                    if (srcMoney < money) ImmediateFuture { }
                    else updateTrees(srcMoney, srcUser, destMoney, destUser, money)
                }
    }

    private fun updateTrees(srcMoney: Long, srcUser: String, destMoney: Long, destUser: String, money: Long): CompletableFuture<Unit> {
        return cashTreeWrapper.treeRemove(channelName, botName, GenericKeyPair(srcMoney, srcUser))
                .thenCompose { cashTreeWrapper.treeRemove(channelName, botName, GenericKeyPair(destMoney, destUser)) }
                .thenCompose { userMoneyTreeWrapper.treeRemove(channelName, botName, GenericKeyPair(0L, srcUser)) }
                .thenCompose { userMoneyTreeWrapper.treeRemove(channelName, botName, GenericKeyPair(0L, destUser)) }

                .thenCompose { cashTreeWrapper.treeInsert(channelName, botName, GenericKeyPair(srcMoney - money, srcUser)) }
                .thenCompose { cashTreeWrapper.treeInsert(channelName, botName, GenericKeyPair(destMoney + money, destUser)) }
                .thenCompose { userMoneyTreeWrapper.treeInsert(channelName, botName, GenericKeyPair(0L, srcUser), value = (srcMoney - money).toString()) }
                .thenCompose { userMoneyTreeWrapper.treeInsert(channelName, botName, GenericKeyPair(0L, destUser), value = (destMoney + money).toString()) }
                .thenApply { }
    }

    /**
     * get user money
     * @param userName String
     * @return CompletableFuture<Long>
     */
    fun getBalance(userName: String): CompletableFuture<Long> {
        return userMoneyTreeWrapper.treeSearch(type = channelName, name = botName, keyPair = GenericKeyPair(0L, userName))
                .thenApply { strMoney -> strMoney?.toLong() ?: 1000L } // if user is not exist in the tree he has 1000
    }

    /**
     * get richest user or null if there is no richest one
     * @return CompletableFuture<String?>
     */
    fun getTop(): CompletableFuture<String?> {
        return getMaxOrNull()
    }

    private fun getMaxOrNull(): CompletableFuture<String?> {
        return cashTreeWrapper.treeGetMax(type = channelName, name = botName)
                .thenCompose<String?> { keyPairValue ->
                    if (keyPairValue == null) ImmediateFuture { null }
                    else {
                        val keyPair = keyPairValue.first
                        val money = keyPair.getFirst()
                        cashTreeWrapper.treeRemove(channelName, botName, keyPair)
                                .thenCompose { cashTreeWrapper.treeGetMax(type = channelName, name = botName) }
                                .thenApply { secKeyPairValue ->
                                    if (secKeyPairValue == null) true
                                    else {
                                        val secKeyPair = secKeyPairValue.first
                                        val secMoney = secKeyPair.getFirst()
                                        money != secMoney
                                    }
                                }
                                .thenCompose { res -> cashTreeWrapper.treeInsert(channelName, botName, keyPair).thenApply { res } }
                                .thenApply { if (it) keyPair.getSecond() else null }
                    }
                }
    }
}