package il.ac.technion.cs.softwaredesign

import java.io.Serializable

data class ComparablePair(val f: Long, val s: Long) : Comparable<ComparablePair>, Serializable {

    fun first(): Long {
        return f
    }

    fun second(): Long {
        return s
    }

    override fun compareTo(other: ComparablePair): Int {
        if (f == other.first()) {
            return other.second().compareTo(s)
        }
        return f.compareTo(other.first())
    }
}