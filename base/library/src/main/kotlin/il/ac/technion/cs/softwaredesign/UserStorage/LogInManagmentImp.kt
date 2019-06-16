package il.ac.technion.cs.softwaredesign.UserStorage

import PersistentDataStructures.Set.DoubleLinkedNode
import PersistentDataStructures.Set.SetsStorage
import il.ac.technion.cs.softwaredesign.DataBase

class LogInManagmentImp(private val db: DataBase<String, User>,
                        private val statDB: DataBase<String, Long>,
                        userListDB: DataBase<String, DoubleLinkedNode<String>>) : LogInManagment {
    private val userNames = SetsStorage(userListDB)

    init {
        userNames.createSet(Entry.UserList.name)
    }

    override fun login(username: String): String {
        val user = getUserByName(username) ?: return ""

        val token = generateToken()

        incremnt(Entry.NumLoggedIn, 1)
        user.loggedIn = true

        db.write(Entry.NameToUser.name + username, user).get()
        db.write(Entry.TokenToUser.name + token, user).get()

        return token
    }

    override fun logout(token: String): Boolean {
        val user = getUserByToken(token) ?: return false
        user.loggedIn = false
        incremnt(Entry.NumLoggedIn, -1)
        db.write(Entry.NameToUser.name + user.userName, user).get()
        db.delete(Entry.TokenToUser.name + token).get()
        return true
    }

    override fun isUserLoggedIn(token: String, username: String): Boolean? {
        return getUserByName(username)?.loggedIn

    }

    override fun register(username: String, password: String): String {
        userNames.add(Entry.UserList.name, username, username)

        val token = generateToken()
        val id = statDB.read(Entry.NumUsers.name).get() ?: 0
        val user = User(userName = username, password = password, id = id, loggedIn = true)
        db.write(Entry.NameToUser.name + username, user).get()
        db.write(Entry.TokenToUser.name + token, user).get()

        incremnt(Entry.NumUsers, 1)
        incremnt(Entry.NumLoggedIn, 1)

        return token
    }

    override fun isUserExists(username: String): Boolean {
        return getUserByName(username) != null
    }

    override fun isValidToken(token: String): Boolean {
        return token.startsWith("%%") && getUserByToken(token) != null
    }

    override fun getUserByName(username: String): User? {
        return db.read(Entry.NameToUser.name + username).get()
    }

    override fun getUserByToken(token: String): User? {
        if (!token.startsWith("%%")) {
            return null
        }
        return db.read(Entry.TokenToUser.name + token).get()
    }

    override fun numActiveUsers(): Long {
        return statDB.read(Entry.NumLoggedIn.name).get() ?: 0
    }

    override fun numTotalUsers(): Long {
        return statDB.read(Entry.NumUsers.name).get() ?: 0
    }

    override fun getUsersInSystem(): Sequence<String> {
        return userNames.asSequence(Entry.UserList.name)
    }

    private fun generateToken(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXTZ0123456789abcdefghiklmnopqrstuvwxyz"
        return "%%" + (1..15)
                .map { allowedChars.random() }
                .joinToString("")
    }

    private fun incremnt(entry: Entry, amount: Int) {
        val total = statDB.read(entry.name).get() ?: 0
        statDB.write(entry.name, total + amount).get()
    }

    private enum class Entry {
        NameToUser,
        TokenToUser,
        NumUsers,
        NumLoggedIn,
        UserList
    }
}