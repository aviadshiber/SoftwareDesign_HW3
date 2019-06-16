package PersistentDataStructures.Tree

/**
 * an interface of generic trees that ensure persistency
 */
interface PersistentTree<Key : Comparable<Key>, V> {
    /**
     * the method add a [key] to the tree if not present
     * if [key is present] then this is an update operation
     * @returns true if a new key was added
     */
    fun add(key: Key, value: V): Boolean

    /**
     * the method removes [key] from the tree if present
     * @returns true if a key was removed
     */
    fun delete(key: Key): Boolean

    /**
     * the method retrieves the value associated with [key] from the tree if present
     * @returns the value associated with [key] if [key] is in the tree null otherwise
     */
    fun get(key: Key): V?

    /**
     * returns a list of the 10 key value pairs which has the largest keys in descending order by key
     */
    fun topN(n: Int): List<Pair<Key, V>>

    /**
     * returns a list of all key value pairs in descending order by key
     */
    fun asSequence(): Sequence<Pair<Key, V>>
}