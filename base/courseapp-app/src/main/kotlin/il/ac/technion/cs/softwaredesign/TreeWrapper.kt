package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.lib.api.CourseBotApi
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import java.util.concurrent.CompletableFuture
import javax.inject.Singleton

class TreeWrapper(private val courseBotApi: CourseBotApi, private val objectPrefix: String) {
    fun treeGet(type: String, name: String): CompletableFuture<List<String>> {
        val newType = "$objectPrefix$type"
        return courseBotApi.treeGet(newType, name)
    }

    fun treeInsert(type: String, name: String, keyPair: GenericKeyPair<Long, String>, value: String = ""): CompletableFuture<Boolean> {
        val newType = "$objectPrefix$type"
        return courseBotApi.treeInsert(newType, name, keyPair, value)
    }

    fun treeRemove(type: String, name: String, keyPair: GenericKeyPair<Long, String>): CompletableFuture<Boolean> {
        val newType = "$objectPrefix$type"
        return courseBotApi.treeRemove(newType, name, keyPair)
    }

    fun treeContains(type: String, name: String, keyPair: GenericKeyPair<Long, String>): CompletableFuture<Boolean> {
        val newType = "$objectPrefix$type"
        return courseBotApi.treeContains(newType, name, keyPair)
    }
}