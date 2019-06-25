package il.ac.technion.cs.softwaredesign.messages

import il.ac.technion.cs.softwaredesign.services.CourseBotApi
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class MessageFactoryImpl @Inject constructor(private val courseBotApi: CourseBotApi) : MessageFactory {
    companion object {
        const val messageType = "_messageIdCounter"
    }

    override fun create(media: MediaType, contents: ByteArray): CompletableFuture<Message> {
        return generateUniqueMessageId().thenApply {
            MessageImpl(it, media = media, contents = contents, created = LocalDateTime.now(), received = null)
        }
    }

    private fun generateUniqueMessageId(): CompletableFuture<Long> {
        return courseBotApi.findCounter(messageType)
                .thenCompose { currId ->
                    if (currId == null)
                        courseBotApi.createCounter(messageType).thenApply { 0L }
                    else
                        courseBotApi.updateCounter(messageType, currId.value + 1L).thenApply { it.value }
                }
    }
}