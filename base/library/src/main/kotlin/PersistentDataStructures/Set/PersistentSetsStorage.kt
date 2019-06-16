package PersistentDataStructures.Set


interface PersistentSetsStorage<V> {
    fun createSet(id: String): Boolean

    fun deleteSet(id: String): Boolean

    fun add(id: String, key: String, value: V): Boolean

    fun remove(id: String, key: String): Boolean

    fun asSequence(id: String): Sequence<V>

    fun get(id: String, key: String): V?

    fun isSetExists(id: String): Boolean
}