package il.ac.technion.cs.softwaredesign

import java.time.LocalDateTime


data class Bot constructor(val id: Long, val token: String, val name: String) {

    var lastSeenMessageTime: LocalDateTime? = null

    val channels = mutableListOf<String>()


}