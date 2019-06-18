package il.ac.technion.cs.softwaredesign.UserStorage

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import il.ac.technion.cs.softwaredesign.DataBase
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture

class UserStorage(private val storage: SecureStorage, id: String) : DataBase<String, User> {
    private val gson = Gson()
    private val id = id.toByteArray(Charset.defaultCharset())

    private fun genKey(key: String): ByteArray {
        return id.plus(key.toByteArray(Charset.defaultCharset()))
    }

    override fun read(key: String): CompletableFuture<User?> {
        return storage.read(genKey(key)).thenApplyAsync {
            val rawData = it?.toString(Charset.defaultCharset()) ?: return@thenApplyAsync null

            if (rawData == "deleted") {
                return@thenApplyAsync null
            }
            return@thenApplyAsync gson.fromJson<User>(rawData)
        }
    }

    override fun write(key: String, value: User): CompletableFuture<Unit> {
        return storage.write(genKey(key), gson.toJson(value).toByteArray(Charset.defaultCharset()))
    }

    override fun delete(key: String): CompletableFuture<Unit> {
        return storage.write(genKey(key), "deleted".toByteArray(Charset.defaultCharset()))
    }
}