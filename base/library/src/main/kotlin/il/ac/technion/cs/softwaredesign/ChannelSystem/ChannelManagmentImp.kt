package il.ac.technion.cs.softwaredesign.ChannelSystem

import PersistentDataStructures.Set.DoubleLinkedNode
import PersistentDataStructures.Set.PersistentSetsStorage
import PersistentDataStructures.Set.SetsStorage
import il.ac.technion.cs.softwaredesign.DataBase

class ChannelManagmentImp(private val db: DataBase<String, DoubleLinkedNode<String>>,
                          private val stats: DataBase<String, Long>) : ChannelManagment {
    private val setsStorageManager: PersistentSetsStorage<String> = SetsStorage(db)

    private enum class StatType {
        Login,
        Total,
        TimeStamp,
        GlobalTimeStamp,
        UserNumChannels
    }

    init {
        stats.write(StatType.GlobalTimeStamp.name, 0).get()
    }

    override fun addChannel(channelname: String) {
        stats.write(StatType.Total.name + channelname, 0).get()
        stats.write(StatType.Login.name + channelname, 0).get()
        grantTimeStamp(channelname)
        setsStorageManager.createSet(channelname)
    }

    override fun deleteChannel(channelname: String) {
        stats.delete(StatType.Total.name + channelname).get()
        stats.delete(StatType.Login.name + channelname).get()
        stats.delete(StatType.TimeStamp.name + channelname).get()
        setsStorageManager.deleteSet(channelname)
    }

    override fun addUserToChannel(username: String, channelname: String) {
        if (stats.read(StatType.TimeStamp.name + channelname).get() == null) {
            grantTimeStamp(channelname)
            addChannel(channelname)
        }
        incremnt(StatType.Total, channelname, 1)
        incremnt(StatType.Login, channelname, 1)
        incremnt(StatType.UserNumChannels, username, 1)

        if (!setsStorageManager.isSetExists(username)) {
            grantTimeStamp(username)
            setsStorageManager.createSet(username)
        }
        setsStorageManager.add(username, channelname, channelname)
        setsStorageManager.add(channelname, username, username)
    }

    override fun removeUserFromChannel(username: String, channelname: String, isUserOnline: Boolean) {
        if (isUserOnline)
            incremnt(StatType.Login, channelname, -1)
        incremnt(StatType.Total, channelname, -1)
        incremnt(StatType.UserNumChannels, username, -1)

        setsStorageManager.remove(username, channelname)
        setsStorageManager.remove(channelname, username)
    }

    override fun loginUser(username: String): Set<Pair<String, Long>> {
        return notifyChannelsAboutUser(username, 1)
    }

    private fun notifyChannelsAboutUser(username: String, delta: Int): Set<Pair<String, Long>> {
        val channelNames = mutableSetOf<Pair<String, Long>>()
        val userChannels = setsStorageManager.asSequence(username)
        userChannels.forEach { channelname ->
            val timeStamp = getTimeStamp(channelname)
            incremnt(StatType.Login, channelname, delta)
            channelNames.add(Pair(channelname, timeStamp))
        }
        return channelNames
    }

    override fun logoutUser(username: String): Set<Pair<String, Long>> {
        return notifyChannelsAboutUser(username, -1)
    }

    override fun getChannelUsersNames(channelname: String): Sequence<String> {
        return setsStorageManager.asSequence(channelname)
    }

    override fun getNumMembersInChannel(channelname: String): Long {
        return stats.read(StatType.Total.name + channelname).get() ?: -1
    }

    override fun getNumLoggedinInChannel(channelname: String): Long {
        return stats.read(StatType.Login.name + channelname).get() ?: -1
    }

    override fun getTimeStamp(channelname: String): Long {
        return stats.read(StatType.TimeStamp.name + channelname).get() ?: -1
    }

    override fun getNumUserChannels(username: String): Long {
        return stats.read(StatType.UserNumChannels.name + username).get() ?: -1
    }

    override fun isChannelExists(channelname: String): Boolean {
        stats.read(StatType.Total.name + channelname).get() ?: return false
        return true
    }

    override fun isChannelNameValid(channelname: String): Boolean {
        return channelname.matches(Regex("#[a-zA-Z0-9_#]*"))
    }

    override fun isUserInChannel(username: String, channelname: String): Boolean {
        db.read(username + channelname).get() ?: return false
        return true
    }

    private fun incremnt(statType: StatType, name: String, amount: Int) {
        val total = stats.read(statType.name + name).get() ?: 0
        stats.write(statType.name + name, total + amount).get()
    }

    private fun grantTimeStamp(name: String) {
        val globalTimeStamp = stats.read(StatType.GlobalTimeStamp.name).get()
        stats.write(StatType.GlobalTimeStamp.name, globalTimeStamp!! + 1).get()
        stats.write(StatType.TimeStamp.name + name, globalTimeStamp).get()
    }
}

