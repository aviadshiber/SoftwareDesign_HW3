package il.ac.technion.cs.softwaredesign.lib.db.dal

import il.ac.technion.cs.softwaredesign.lib.utils.BalancedStorageTree
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.io.Serializable
import java.util.concurrent.CompletableFuture

data class KeyPair<K : Comparable<K>>(private var first: K, private val second: K) : Comparable<KeyPair<K>>, Serializable{

    fun setFirst(value: K) {
        this.first = value
    }

    fun getFirst() = first

    fun getSecond() = second

    override fun compareTo(other: KeyPair<K>): Int {
        val firstCompare :Int = this.first.compareTo(other.first)
        return if (firstCompare != 0) firstCompare.times(-1) else this.second.compareTo(other.second)
    }
}

class TreeDataAccessLayer<T>(storage: CompletableFuture<SecureStorage>, type: String = ""): DataAccessLayer<KeyPair<Long>, T> {
    private val tree = BalancedStorageTree<KeyPair<Long>, T>(storage, type = type)

    override fun readObject(id: KeyPair<Long>, keys: List<String>): CompletableFuture<T?> {
        return tree.search(id)
    }

    override fun writeObject(id: KeyPair<Long>, value: T): CompletableFuture<Unit> {
        return tree.insert(id,value).thenApply { }
    }

    override fun deleteObject(id: KeyPair<Long>, keys: List<String>): CompletableFuture<T?> {
        return tree.search(id).thenCompose { v -> tree.delete(id).thenApply { v } }
    }

    fun getSequence(n: Int): CompletableFuture<Sequence<Pair<KeyPair<Long>, T>>> {
        return tree.asList(n).thenApply { it.asSequence() }
    }

    fun getMax(): CompletableFuture<Pair<KeyPair<Long>, T>?> {
        return tree.rightmost()
    }

    fun isMaxUnique(): CompletableFuture<Boolean> {
        return getMax().thenCompose { keyPairValue ->
            if (keyPairValue == null) ImmediateFuture { false }
            else {
                val keyPair = keyPairValue.first
                val firstKeyToCompare = keyPair.getFirst()
                deleteObject(keyPair, emptyList())
                        .thenCompose { getMax() }
                        .thenApply { secKeyPairValue ->
                            if (secKeyPairValue == null) true
                            else {
                                val secKeyPair = secKeyPairValue.first
                                val secKeyToCompare = secKeyPair.getFirst()
                                firstKeyToCompare != secKeyToCompare
                            }
                        }
                        .thenCompose { res ->
                            writeObject(keyPairValue.first, keyPairValue.second).thenApply { res }
                        }
            }
        }
    }
}