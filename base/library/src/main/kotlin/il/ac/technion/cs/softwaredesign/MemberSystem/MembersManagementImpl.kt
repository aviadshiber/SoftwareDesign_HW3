package il.ac.technion.cs.softwaredesign.MemberSystem

import il.ac.technion.cs.softwaredesign.DataBase

class MembersManagementImpl(private val db: DataBase<String, MembersManagement.Privilege>) : MembersManagement {
    override fun isMember(memberName: String, channelName: String): Boolean {
        return db.read(memberName + channelName).get() != null
    }

    override fun isOperator(memberName: String, channelName: String): Boolean {
        return db.read(memberName + channelName).get() == MembersManagement.Privilege.Operator
    }

    override fun isAdmin(memberName: String): Boolean {
        return db.read(memberName).get() == MembersManagement.Privilege.Admin
    }

    override fun joinChannel(memberName: String, channelName: String) {
        db.write(memberName + channelName, MembersManagement.Privilege.Member).get()
    }

    override fun leaveChannel(memberName: String, channelName: String) {
        db.delete(memberName + channelName).get()
    }

    override fun makeAdmin(userName: String) {
        db.write(userName, MembersManagement.Privilege.Admin).get()
    }

    override fun makeOperator(memberName: String, channelName: String) {
        db.write(memberName + channelName, MembersManagement.Privilege.Operator).get()
    }

    override fun getMemberChannelStatus(memberName: String, channelName: String): MembersManagement.Privilege? {
        return db.read(memberName + channelName).get()
    }
}