package il.ac.technion.cs.softwaredesign.lib.api

import il.ac.technion.cs.softwaredesign.lib.api.model.*
import il.ac.technion.cs.softwaredesign.lib.db.Counter
import il.ac.technion.cs.softwaredesign.lib.db.Database
import il.ac.technion.cs.softwaredesign.lib.db.dal.GenericKeyPair
import il.ac.technion.cs.softwaredesign.lib.utils.generateToken
import io.github.vjames19.futures.jdk8.ImmediateFuture
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
     * @return CompletableFuture<Bot?>
     */
    fun createBot(name: String, token: String, id: Long): CompletableFuture<Bot?> {
        return db.document(Bot.TYPE)
                .create(name)
                .set(Bot.KEY_BOT_ID, id)
                .set(Bot.KEY_BOT_NAME, name)
                .set(Bot.KEY_BOT_TOKEN, token)
                .set(Bot.KEY_BOT_LAST_SEEN_MSG_TIME, "")
                .set(Bot.KEY_BOT_CALCULATION_TRIGGER, "")
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

    fun createVoteAnswer(id: String, answerIndex: Long): CompletableFuture<VoteAnswer> {
        return db.document(VoteAnswer.TYPE)
                .create(id)
                .set(VoteAnswer.KEY_ANSWER_INDEX, answerIndex)
                .executeFor(VoteAnswer::class.java).thenApply { it!! }
    }

    fun updateVoteAnswer(id: String, answerIndex: Long): CompletableFuture<VoteAnswer> {
        return db.document(VoteAnswer.TYPE)
                .update(id)
                .set(VoteAnswer.KEY_ANSWER_INDEX, answerIndex)
                .executeFor(VoteAnswer::class.java).thenApply { it!! }
    }

    fun findVoteAnswer(id: String): CompletableFuture<VoteAnswer?> {
        val keys = listOf(VoteAnswer.KEY_ANSWER_INDEX)
        return db.document(VoteAnswer.TYPE)
                .find(id, keys)
                .executeFor(VoteAnswer::class.java)
    }

    fun deleteVoteAnswer(id: String): CompletableFuture<VoteAnswer?> {
        return db.document(VoteAnswer.TYPE)
                .delete(id, listOf(VoteAnswer.KEY_ANSWER_INDEX))
                .executeFor(VoteAnswer::class.java)
    }

    fun createCounter(id: String): CompletableFuture<Counter> {
        return db.document(Counter.TYPE)
                .create(id)
                .set(Counter.KEY_VALUE, 0L)
                .executeFor(Counter::class.java).thenApply { it!! }
    }

    fun updateCounter(id: String, value: Long): CompletableFuture<Counter> {
        return db.document(Counter.TYPE)
                .update(id)
                .set(Counter.KEY_VALUE, value)
                .executeFor(Counter::class.java).thenApply { it!! }
    }

    fun findCounter(id: String): CompletableFuture<Counter?> {
        val keys = listOf(Counter.KEY_VALUE)
        return db.document(Counter.TYPE)
                .find(id, keys)
                .executeFor(Counter::class.java)
// no need because we have delete function
//                .thenApply {
//                    if (it == null || it.value == -1L) null
//                    else it
//                }
    }

    fun deleteCounter(id: String): CompletableFuture<Counter?> {
        return db.document(Counter.TYPE)
                .delete(id, listOf(Counter.KEY_VALUE))
                .executeFor(Counter::class.java)
    }

    fun createSurvey(id: String, question: String): CompletableFuture<Survey> {
        return db.document(Survey.TYPE)
                .create(id)
                .set(Survey.KEY_QUESTION, question)
                .executeFor(Survey::class.java).thenApply { it!! }
    }

    fun updateSurvey(id: String, vararg pairs: Pair<String, Any>): CompletableFuture<Survey> {
        return db.document(Survey.TYPE)
                .update(id)
                .set(*pairs)
                .executeFor(Survey::class.java).thenApply { it!! }
    }

    fun findSurvey(id: String): CompletableFuture<Survey?> {
        val keys = listOf(Survey.KEY_QUESTION)
        return db.document(Survey.TYPE)
                .find(id, keys)
                .executeFor(Survey::class.java)
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
     * Insert a value to a list
     * @param type The list type. Should be unique tag associated with a database object
     * @param name Ths list name. Should be unique for each instance of a database object
     * @return true if the element was successfully inserted, or false if and element with the given key
     * already exists in the tree
     */
    fun treeInsert(type: String, name: String, keyPair: GenericKeyPair<Long, String>, value: String = ""): CompletableFuture<Boolean> {
        return db.tree(type, name).insert(keyPair, value)
    }

    // example:
    // db.list(Channel.LIST_USERS, name)
    /**
     * Remove a value from a list
     * @param type The list type. Should be unique tag associated with a database object
     * @param name Ths list name. Should be unique for each instance of a database object
     * @return true if the element was successfully deleted, or false if and element with the given key
     * does not exists in the tree
     */
    fun treeRemove(type: String, name: String, keyPair: GenericKeyPair<Long, String>): CompletableFuture<Boolean> {
        return db.tree(type, name).delete(keyPair)
    }

    fun treeClean(type: String, name: String): CompletableFuture<Unit> {
        return ImmediateFuture { db.tree(type, name).clean() }
    }

    fun treeContains(type: String, name: String, keyPair: GenericKeyPair<Long, String>): CompletableFuture<Boolean> {
        return db.tree(type, name).search(keyPair).thenApply { it != null }
    }

    /**
     * Retrieve all tree's keys
     * @param type The tree type
     * @param name Ths tree name
     * @return The desired tree's strings extracted from keys, or empty list if tree does not exist
     */
    fun treeGet(type: String, name: String): CompletableFuture<List<String>> {
        return db.tree(type, name).asSequence()
                .thenApply { seq -> seq.toList() }
                .thenApply { lst -> lst.map { it.first.getSecond() } }
    }

    fun treeToSequence(type: String, name: String) = db.tree(type, name).asSequence()

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