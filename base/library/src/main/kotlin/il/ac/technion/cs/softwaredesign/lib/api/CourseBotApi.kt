package il.ac.technion.cs.softwaredesign.lib.api

import il.ac.technion.cs.softwaredesign.lib.api.model.*
import il.ac.technion.cs.softwaredesign.lib.api.model.UsersMetadata.Companion.CHANNELS_BY_ONLINE_USERS
import il.ac.technion.cs.softwaredesign.lib.api.model.UsersMetadata.Companion.CHANNELS_BY_USERS
import il.ac.technion.cs.softwaredesign.lib.api.model.UsersMetadata.Companion.USERS_BY_CHANNELS
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.lib.utils.generateToken
import il.ac.technion.cs.softwaredesign.lib.utils.thenForward
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class CourseBotApi @javax.inject.Inject constructor(private val db: Database) {

    companion object {
        const val KEY_COUNTERS = "counters"
    }

    private fun getOrCreateCounters(): CompletableFuture<UsersMetadata> {
        return db.document(UsersMetadata.TYPE)
                .find(KEY_COUNTERS, listOf(UsersMetadata.KEY_TOTAL_USERS, UsersMetadata.KEY_ONLINE_USERS))
                .executeFor(UsersMetadata::class.java)
                .thenCompose { metadata -> if (metadata == null) db.document(UsersMetadata.TYPE)
                        .create(KEY_COUNTERS) // KEY_COUNTER its the identifier, instance name
                        .set(UsersMetadata.KEY_TOTAL_USERS to 0, UsersMetadata.KEY_ONLINE_USERS to 0)
                        .executeFor(UsersMetadata::class.java)
                        .thenApply { it!! }
                else CompletableFuture.completedFuture(metadata)
                }
    }

    /**
     * Creates a new session in the storage
     * @param botId the bot that belongs to the session
     * @return Session model representing the session
     */
    fun createSession(botId: String): CompletableFuture<Session?> {
        return db.document(Session.TYPE)
                .create(generateToken())
                .set(Session.KEY_USER, botId)
                .executeFor(Session::class.java)
    }

    /**
     * Deletes a session from the storage
     * @param token the session to be deleted
     * @return The deleted session, or null of the session does not exist
     */
    fun deleteSession(token: String): CompletableFuture<Session?> {
        return db.document(Session.TYPE)
                .delete(token, listOf(Session.KEY_USER))
                .executeFor(Session::class.java)
    }

    /**
     * find a bot in the storage
     * @param name
     * @return Bot model representing the Bot, or null if the bot does not exist
     */
    fun findBot(name: String): CompletableFuture<Bot?> {
        val keys = listOf(Bot.KEY_BOT_NAME, Bot.KEY_BOT_TOKEN, Bot.KEY_BOT_LAST_SEEN_MSG_TIME, Bot.KEY_BOT_CALCULATION_TRIGGER)
        return db.document(Bot.TYPE).find(name, keys).executeFor(Bot::class.java)
    }

    /**
     * Create new Bot
     * @param id Long
     * @param name String
     * @param token String
     * @param lastSeenMessageTime LocalDateTime
     * @param calculationTrigger String
     * @return CompletableFuture<Bot?>
     */
    fun createBot(name: String, token: String, id: Long
            //, lastSeenMessageTime: LocalDateTime, calculationTrigger: String TODO: check if needed
    ): CompletableFuture<Bot?> {
        return db.document(Bot.TYPE)
                .create(name)
                .set(Bot.KEY_BOT_ID, id)
                .set(Bot.KEY_BOT_NAME, name)
                .set(Bot.KEY_BOT_TOKEN, token)
                //.set(Bot.KEY_BOT_LAST_SEEN_MSG_TIME, lastSeenMessageTime)
                //.set(Bot.KEY_BOT_CALCULATION_TRIGGER, calculationTrigger)
                .executeFor(Bot::class.java)
    }

    fun updateBot(name: String, vararg pairs: Pair<String, Any>): CompletableFuture<Bot?> {
        return db.document(User.TYPE)
                .update(name)
                .set(*pairs)
                .executeFor(Bot::class.java)
    }

    fun findChannel(name: String): CompletableFuture<Channel?> {
        val keys = listOf(Channel.KEY_NAME, Channel.KEY_CHANNEL_ID)
        return db.document(Channel.TYPE)
                .find(name, keys)
                .executeFor(Channel::class.java)
    }

    fun createChannel(name: String, /*creator: String,*/ id: Long): CompletableFuture<Channel> {
        return db.document(Channel.TYPE)
                .create(name)
                .set(Channel.KEY_NAME, name)
                .set(Channel.KEY_CHANNEL_ID, id)
                .executeFor(Channel::class.java)
                //.thenCompose { channel -> db.list(Channel.LIST_USERS, name).insert(creator).thenApply { channel } }
                //.thenCompose { channel -> db.list(Channel.LIST_OPERATORS, name).insert(creator).thenApply { channel } }
                //.thenCompose { channel -> db.list(Channel.LIST_BOTS, name).insert(creator).thenApply { channel } }
                //.thenForward(updateMetadataBy(USERS_BY_CHANNELS, creator, 1))
                //.thenForward(createMetadata(CHANNELS_BY_USERS, name, 0))
                //.thenForward(createMetadata(CHANNELS_BY_ONLINE_USERS, name, 0))
                .thenApply { it!! }
    }

    fun updateChannel(name: String, vararg pairs: Pair<String, Any>): CompletableFuture<Channel?> {
        return db.document(Channel.TYPE)
                .update(name)
                .set(*pairs)
                .executeFor(Channel::class.java)
    }

    fun deleteChannel(name: String): CompletableFuture<Channel?> {
        return db.document(Channel.TYPE)
                .delete(name, listOf())
                .executeFor(Channel::class.java)
                //.thenCompose { channel -> deleteMetadata(CHANNELS_BY_USERS,name).thenApply { channel } }
                //.thenCompose { channel -> deleteMetadata(CHANNELS_BY_ONLINE_USERS, name).thenApply { channel }}
    }

    /**
     * Insert a value to a list
     * @param type The list type. Should be unique tag associated with a database object
     * @param name Ths list name. Should be unique for each instance of a database object
     */
    fun listInsert(type: String, name: String, value: String): CompletableFuture<Unit> {
        return db.list(type, name).contains(value).thenCompose { contains ->
            if (!contains) db.list(type, name).insert(value) else CompletableFuture.completedFuture(Unit)
        }
    }

    // example:
    // db.list(Channel.LIST_USERS, name)
    /**
     * Remove a value from a list
     * @param type The list type. Should be unique tag associated with a database object
     * @param name Ths list name. Should be unique for each instance of a database object
     */
    fun listRemove(type: String, name: String, value: String): CompletableFuture<Unit> {
        return db.list(type, name).remove(value)
    }


    fun listContains(type: String, name: String, value: String): CompletableFuture<Boolean> {
        return db.list(type, name).contains(value)
    }

    /**
     * Retreive a list
     * @param type The list type
     * @param name Ths list name
     * @return The desired list, or an empty list if it was not yet written to
     */
    fun listGet(type: String, name: String): CompletableFuture<List<String>> {
        return db.list(type, name).asSequence()
                .thenApply { sequence -> sequence.map { it.second }.toList() }
    }

    /**
     * returns the metadata about Users
     * counters for total users, online users
     * @return UserMetadata model
     * @see UsersMetadata for more information about the model
     */
    fun getUsersMetadata(): CompletableFuture<UsersMetadata> {
        return getOrCreateCounters()
    }

    fun updateTotalUsers(changeBy: Int, metadata: UsersMetadata): CompletableFuture<UsersMetadata> {
        return db.document(UsersMetadata.TYPE)
                .update(KEY_COUNTERS)
                .set(UsersMetadata.KEY_TOTAL_USERS, metadata.totalUsers.plus(changeBy))
                .executeFor(UsersMetadata::class.java).thenApply { it!! }
    }

    fun updateOnlineUsers(changeBy: Int, metadata: UsersMetadata): CompletableFuture<UsersMetadata> {
        return  db.document(UsersMetadata.TYPE)
                .update(KEY_COUNTERS)
                .set(UsersMetadata.KEY_ONLINE_USERS, metadata.onlineUsers.plus(changeBy))
                .executeFor(UsersMetadata::class.java).thenApply { it!! }
    }

    /**
     * Find a metadata counter for a given label and key.
     * For example, finding the number of logged in users for a given channel:
     * <pre>
     * {@code
     *  api.findMetadata(CHANNELS_BY_USERS, channel.name)
     * }
     * </pre>
     */
    fun findMetadata(label: String, key: String) : CompletableFuture<Long?> {
        return db.metadata(label).find(key)
    }

    /**
     * Create a metadata counter for a given label and key.
     */
    fun createMetadata(label: String, key: String, value: Long): CompletableFuture<Unit>{
        return db.metadata(label).create(key, value)
    }

    /**
     * Delete a metadata counter for a given label and key.
     */
    fun deleteMetadata(label: String, key: String) : CompletableFuture<Long?>{
        return db.metadata(label).delete(key)
    }

    /**
     * Update a metadata counter for a given label and key.
     * You are responsible for handling metadata changes yourself, so
     * For example, updating the number of logged in users for a given channel:
     * <pre>
     * {@code
     *  api.updateMetadata(CHANNELS_BY_USERS, channel.name, 100) // This will set the number of logged in users to 100
     * }
     * </pre>
     */
    fun updateMetadata(label: String, key: String, value: Long): CompletableFuture<Unit> {
        return db.metadata(label).update(key, value)
    }

    /**
     * Update a metadata counter by an incremental value for a given label and key.
     * For example, incrementing the number of logged in users for a given channel by 1:
     * <pre>
     * {@code
     *  api.updateMetadataBy(CHANNELS_BY_USERS, channel.name, 1)
     * }
     * </pre>
     */
    fun updateMetadataBy(label: String, key: String, changeBy: Long): CompletableFuture<Unit> {
        return findMetadata(label, key).thenCompose { oldVal ->
            if (oldVal == null) createMetadata(label, key, changeBy)
            else {
                updateMetadata(label, key, oldVal.plus(changeBy))
            }
        }
    }

}