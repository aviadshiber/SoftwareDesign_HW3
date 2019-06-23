package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import java.util.concurrent.CompletableFuture

class CashBalance constructor(private val channelName:String,private val botName:String,
                              private val courseBotApi: CourseBotApi) {

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
        TODO()
    }

    /**
     * get user money
     * @param userName String
     * @return CompletableFuture<Long>
     */
    fun getBalance(userName:String):CompletableFuture<Long>{
        return userMoneyTreeWrapper.treeSearch(type = channelName, name = botName, keyPair = GenericKeyPair(0L, userName))
                .thenApply { strMoney -> strMoney?.toLong() ?: 1000L } // if user is not exist in the tree he has 1000
    }

    /**
     * get richest user or null if there is no richest one
     * @return CompletableFuture<String?>
     */
    fun getTop():CompletableFuture<String?>{
        TODO()
    }

}