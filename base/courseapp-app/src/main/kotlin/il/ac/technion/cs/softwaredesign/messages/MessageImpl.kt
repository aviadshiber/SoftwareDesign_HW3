package il.ac.technion.cs.softwaredesign.messages

import java.time.LocalDateTime

class MessageImpl(override var id: Long = -1L,
                  override var media: MediaType = MediaType.TEXT,
                  override var contents: ByteArray = byteArrayOf(),
                  override var created: LocalDateTime = LocalDateTime.now(),
                  override var received: LocalDateTime? = null) : Message