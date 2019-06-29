package il.ac.technion.cs.softwaredesign.tests

import com.google.inject.Inject
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.CourseApp
import il.ac.technion.cs.softwaredesign.ListenerCallback
import il.ac.technion.cs.softwaredesign.exceptions.*
import il.ac.technion.cs.softwaredesign.lib.utils.thenDispose
import il.ac.technion.cs.softwaredesign.messages.Message
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

typealias UserName = String
typealias Password = String
typealias Token = String
typealias ChannelName = String
typealias UserNames = MutableSet<UserName>
typealias AdminName = String

@Singleton
class FakeCourseApp @Inject constructor(
        private val logger: Logger,
        private val userNameToPassword: MutableMap<UserName, Password>,
        private val tokenToUserName: MutableMap<Token, UserName>,
        private val userNameToToken: MutableMap<UserName, Token>,
        private val admins: MutableSet<AdminName>
) : CourseApp {


    private val channelsUsers: MutableMap<ChannelName, UserNames> = mutableMapOf()
    private val userNameToListeners: MutableMap<UserName, MutableList<ListenerCallback>> = mutableMapOf()
    private val userNameToPendingSourceMessages: MutableMap<UserName, MutableList<Pair<String, Message>>> = mutableMapOf()
    /**
     * Log in a user identified by [username] and [password], returning an authentication token that can be used in
     * future calls. If this username did not previously log in to the system, it will be automatically registered with
     * the provided password. Otherwise, the password will be checked against the previously provided password.
     *
     * Note: Allowing enumeration of valid usernames is not a good property for a system to have, from a security
     * standpoint. But this is the way this system will work.
     *
     * If this is the first user to be registered, it will be made an administrator.
     *
     * This is a *create* command.
     *
     * @throws NoSuchEntityException If the password does not match the username.
     * @throws UserAlreadyLoggedInException If the user is already logged-in.
     * @return An authentication token to be used in other calls.
     */
    override fun login(username: String, password: String): CompletableFuture<String> {
        logger.fine { "login(%s,%s)".format(username, password) }
        return ImmediateFuture {
            if (userNameToPassword.getOrPut(username, { password }) != password) {
                throw NoSuchEntityException()
            }

            if (userNameToToken.contains(username)) {
                throw UserAlreadyLoggedInException()
            }

            if (userNameToToken.isEmpty()) {
                admins.add(username)
            }

            userNameToToken[username] = username
            tokenToUserName[username] = username
            logger.fine { "tokenToUserName[%s] = %s".format(username, username) }
            userNameToListeners[username] = mutableListOf()
            userNameToPendingSourceMessages[username] = mutableListOf()

            username
        }
    }


    /**
     * The user identified by [token] will join [channel]. If the channel does not exist, it is created only if [token]
     * identifies a user who is an administrator.
     *
     * Valid names for channels start with `#`, then have any number of English alphanumeric characters, underscores
     * (`_`) and hashes (`#`).
     *
     * This is a *create* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NameFormatException If [channel] is not a valid name for a channel.
     * @throws UserNotAuthorizedException If [channel] does not exist and [token] belongs to a user who is not an
     * administrator.
     */
    override fun channelJoin(token: String, channel: String): CompletableFuture<Unit> {
        logger.info { "channelJoin(%s,%s)".format(token, channel) }

        return ImmediateFuture {
            logger.info { "tokenToUserName[token]=%s".format(tokenToUserName[token]) }
            val username = tokenToUserName[token] ?: throw InvalidTokenException()

            if (!"""#[0-9A-Za-z_#]*""".toRegex().matches(channel)) {
                throw NameFormatException()
            }

            var userNames = channelsUsers[channel]

            if (null == userNames) {
                if (!admins.contains(username)) {
                    throw UserNotAuthorizedException()
                }

                userNames = mutableSetOf()
                channelsUsers[channel] = userNames
            }
            userNames.add(username)
        }.thenDispose()
    }

    /**
     * The user identified by [token] will exit [channel].
     *
     * If the last user leaves a channel, the channel will be destroyed and its name will be available for re-use. The
     * first user to join the channel becomes an operator.
     *
     * This is a *delete* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [token] identifies a user who is not a member of [channel], or [channel] does
     * does exist.
     */
    override fun channelPart(token: String, channel: String): CompletableFuture<Unit> {
        logger.fine { "channelPart(%s,%s)".format(token, channel) }
        return ImmediateFuture {
            val username = tokenToUserName[token] ?: throw InvalidTokenException()
            val userNames = channelsUsers.getOrDefault(channel, mutableSetOf())

            if (!userNames.remove(username)) {
                throw NoSuchEntityException()
            }

            if (userNames.isEmpty()) {
                channelsUsers.remove(channel)
            }
        }
    }


    /**
     * Indicate [username]'s membership in [channel]. A user is still a member of a channel when logged off.
     *
     * This is a *read* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] is not an administrator or member of this channel.
     * @return True if [username] exists and is a member of [channel], false if it exists and is not a member, and null
     * if it does not exist.
     */
    override fun isUserInChannel(token: String, channel: String, username: String): CompletableFuture<Boolean?> {
        logger.fine { "isUserInChannel(%s,%s,%s)".format(token, channel, username) }
        return ImmediateFuture {
            val queryUserName = tokenToUserName[token] ?: throw InvalidTokenException()
            val userNames = channelsUsers[channel] ?: throw NoSuchEntityException()

            if (!admins.contains(queryUserName) && !userNames.contains(queryUserName)) {
                throw UserNotAuthorizedException()
            }

            if (userNames.contains(username)) true else (if (userNameToPassword.contains(username)) false else null)
        }
    }


    /**
     * Adds a listener to this Course App instance. See Observer design pattern for more information.
     *
     * See the assignment PDF for semantics.
     *
     * This is an *update* command.
     *
     * @throws InvalidTokenException if the auth [token] is invalid.
     */
    override fun addListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        logger.fine { "addListener from (%s)".format(token) }
        return ImmediateFuture {
            val userName = tokenToUserName[token] ?: throw InvalidTokenException()
            userNameToListeners[userName]!!.add(callback)
            assert(userNameToPendingSourceMessages[userName]!!.isEmpty())
        }.thenDispose()
    }

    /**
     * Remove a listener from this Course App instance. See Observer design pattern for more information.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [callback] is not registered with this instance.
     */
    override fun removeListener(token: String, callback: ListenerCallback): CompletableFuture<Unit> {
        logger.fine { "removeListener from (%s)".format(token) }
        return ImmediateFuture {
            val userName = tokenToUserName[token] ?: throw InvalidTokenException()
            userNameToListeners[userName]!!.remove(callback)
        }.thenDispose()

    }

    /**
     * Send a message to a channel from the user identified by [token]. Listeners will be notified, source will be
     * "[channel]@<user>" (including the leading `"`). So, if `gal` sent a message to `#236700`, the source will be
     * `#236700@gal`.
     *
     * This is an *update* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [channel] does not exist.
     * @throws UserNotAuthorizedException If [token] identifies a user who is not a member of [channel].
     */
    override fun channelSend(token: String, channel: String, message: Message): CompletableFuture<Unit> {
        logger.fine { "channelSend from %s in %s message: \"%s\"".format(token, channel, String(message.contents)) }
        return ImmediateFuture {
            val userName = tokenToUserName[token] ?: throw InvalidTokenException()
            val userNames = channelsUsers[channel] ?: throw NoSuchEntityException()
            val source = "%s@%s".format(channel, userName)

            if (!userNames.contains(userName)) {
                throw UserNotAuthorizedException()
            }

            userNames.forEach { name ->
                send(name, source, message)
            }
        }.thenDispose()
    }

    private fun send(userName: String, source: String, message: Message) {
        val listeners = userNameToListeners[userName]!!

        if (listeners.isEmpty()) {
            userNameToPendingSourceMessages[userName]!!.add(Pair(source, message))
        } else {
            listeners.forEach { listener ->
                listener(source, message).join()
            }
        }
    }

    /**
     * Sends a message to all users from an admin identified by [token]. Listeners will be notified, source is
     * "BROADCAST".
     *
     * This is an *update* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws UserNotAuthorizedException If [token] does not identify an administrator.
     */
    override fun broadcast(token: String, message: Message): CompletableFuture<Unit> {
        logger.fine { "broadcast from %s message: \"%s\"".format(token, String(message.contents)) }
        return ImmediateFuture {
            val userName = tokenToUserName[token] ?: throw InvalidTokenException()

            if (!admins.contains(userName)) {
                throw UserNotAuthorizedException()
            }

            userNameToToken.keys.forEach { name ->
                send(name, "broadcast", message)
            }
        }
    }

    /**
     * Sends a private message from the user identified by [token] to [user]. Listeners will be notified, source will be
     * "@<user>", where <user> is the user identified by [token]. So, if `gal` sent `matan` a message, that source will
     * be `@gal`.
     *
     * This is an *update* command.
     *
     * @throws InvalidTokenException If the auth [token] is invalid.
     * @throws NoSuchEntityException If [user] does not exist.
     */
    override fun privateSend(token: String, user: String, message: Message): CompletableFuture<Unit> {
        logger.fine { "privateSend from %s to %s message: \"%s\"".format(token, user, String(message.contents)) }
        return ImmediateFuture {
            val userName = tokenToUserName[token] ?: throw InvalidTokenException()

            if (!userNameToPassword.containsKey(user)) {
                throw NoSuchEntityException()
            }

            send(user, "@%s".format(userName), message)
        }
    }

    fun restore(courseAppImpl: FakeCourseApp) {
        userNameToPassword.clear()
        userNameToToken.clear()
        tokenToUserName.clear()
        channelsUsers.clear()
        admins.clear()
        userNameToListeners.clear()
        userNameToPendingSourceMessages.clear()

        userNameToPassword.putAll(courseAppImpl.userNameToPassword)
        userNameToToken.putAll(courseAppImpl.userNameToToken)
        tokenToUserName.putAll(courseAppImpl.tokenToUserName)
        channelsUsers.putAll(courseAppImpl.channelsUsers)
        admins.addAll(courseAppImpl.admins)
        userNameToPassword.forEach { userNameToListeners[it.key] = mutableListOf() }
        userNameToPendingSourceMessages.putAll(courseAppImpl.userNameToPendingSourceMessages)

    }

    override fun fetchMessage(token: String, id: Long): CompletableFuture<Pair<String, Message>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun logout(token: String): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isUserLoggedIn(token: String, username: String): CompletableFuture<Boolean?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun makeAdministrator(token: String, username: String): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channelMakeOperator(token: String, channel: String, username: String): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun channelKick(token: String, channel: String, username: String): CompletableFuture<Unit> {
        //does not implement fully the api, it is just for the tests
        logger.info { "channelKick(%s,%s)".format(token, channel) }

        return ImmediateFuture {
            logger.info { "tokenToUserName[token]=%s".format(tokenToUserName[token]) }
            val adminUsername = tokenToUserName[token] ?: throw InvalidTokenException()

            if (!"""#[0-9A-Za-z_#]*""".toRegex().matches(channel)) {
                throw NameFormatException()
            }
            val usersInChannel = channelsUsers[channel]
            usersInChannel?.remove(username)
        }.thenDispose()
    }

    override fun numberOfActiveUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun numberOfTotalUsersInChannel(token: String, channel: String): CompletableFuture<Long> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}