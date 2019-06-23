package il.ac.technion.cs.softwaredesign

import java.util.concurrent.CompletableFuture

class CashBalance constructor(private val channelName:String,private val botName:String) {

    //TODO: add tree of cash
    fun transfer(srcUser: String, destUser: String, money: Long): CompletableFuture<Unit> {
        TODO()
    }

    fun getBalance(userName:String):CompletableFuture<Long>{
        TODO()
    }

    fun getTop():CompletableFuture<String?>{
        TODO()
    }

}