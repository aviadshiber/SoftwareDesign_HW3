package il.ac.technion.cs.softwaredesign.UserStorage

data class User(val userName: String, val password: String, val id: Long, var loggedIn: Boolean)