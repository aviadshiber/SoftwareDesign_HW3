package il.ac.technion.cs.softwaredesign.UserStorage

/**
 * an interface reresenting a login system with token identifiers
 * the tokens are unique and data is persistent
 * this interface is simple without exceptions or complex logic
 * with create/update operations that always succeed
 * and queries which can fail
 * we also provide statistics in the form of current number of registered/logged in users
 *
 *
 *
 */

interface LogInManagment {

    fun login(username: String): String

    fun logout(token: String): Boolean

    fun register(username: String, password: String): String

    fun isUserLoggedIn(token: String, username: String): Boolean?

    fun isUserExists(username: String): Boolean

    fun isValidToken(token: String): Boolean

    fun getUserByName(username: String): User?

    fun getUserByToken(token: String): User?

    fun numTotalUsers(): Long

    fun numActiveUsers(): Long

    fun getUsersInSystem(): Sequence<String>
}