package il.ac.technion.cs.softwaredesign.MemberSystem

interface MembersManagement {

    /**
     * handles the mapping between members and channels
     * enables queries and Privilege management
     */

    enum class Privilege {
        Member,
        Operator,
        Admin,
        User
    }

    /**
     * check if [memberName] is part of channel [channelName]
     * @return true if [memberName] is a Member or Operator of [channelName]
     */
    fun isMember(memberName: String, channelName: String): Boolean


    /**
     * check if [memberName] is an Operator of channel [channelName]
     * @return true if [memberName] is an Operator of [channelName]
     */
    fun isOperator(memberName: String, channelName: String): Boolean


    /**
     * check if [memberName] is an Admin of the System
     * @return true if [memberName] is an Admin
     */
    fun isAdmin(memberName: String): Boolean


    /**
     * add[memberName] as a Member of channel [channelName]
     */
    fun joinChannel(memberName: String, channelName: String)


    /**
     * remove[memberName] from channel [channelName]
     * resulting is isMember([memberName],[channelName] returning false
     */
    fun leaveChannel(memberName: String, channelName: String)


    /**
     * make [userName] an Admin of the system
     */
    fun makeAdmin(userName: String)

    /**
     * make [memberName] an Operator of [channelName], making him part of channel if he was not before.
     * as a result isOperator([memberName],[channelName]) will return true
     */
    fun makeOperator(memberName: String, channelName: String)

    /**
     * retrieve the Privilege level of member [memberName] in channel [channelName]
     * @return the Privilege level of the member if he is part of the channel, null if he is not
     */
    fun getMemberChannelStatus(memberName: String, channelName: String): Privilege?
}
