package il.ac.technion.cs.softwaredesign.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


internal val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss:SSS")


internal fun LocalDateTime.convertToString(): String {
    return this.format(formatter)
}

internal fun String.convertToLocalDateTime(): LocalDateTime {
    return LocalDateTime.parse(this, formatter)
}

