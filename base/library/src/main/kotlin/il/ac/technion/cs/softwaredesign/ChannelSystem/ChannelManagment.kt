package il.ac.technion.cs.softwaredesign.ChannelSystem

/**
 * handles the mappings between channels and users
 * as well as queries and statistics about channels
 */

interface ChannelManagment {
    fun addChannel(channelname: String)

    fun deleteChannel(channelname: String)

    fun addUserToChannel(username: String, channelname: String)

    fun removeUserFromChannel(username: String, channelname: String, isUserOnline: Boolean)

    fun logoutUser(username: String): Set<Pair<String, Long>>

    fun loginUser(username: String): Set<Pair<String, Long>>

    fun getChannelUsersNames(channelname: String): Sequence<String>

    fun getNumMembersInChannel(channelname: String): Long

    fun getNumLoggedinInChannel(channelname: String): Long

    fun getTimeStamp(channelname: String): Long

    fun getNumUserChannels(username: String): Long

    fun isChannelExists(channelname: String): Boolean

    fun isChannelNameValid(channelname: String): Boolean

    fun isUserInChannel(username: String, channelname: String): Boolean
}