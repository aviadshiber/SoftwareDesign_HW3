package PersistentDataStructures.Set

import il.ac.technion.cs.softwaredesign.DataBase
import java.io.Serializable

class DoubleLinkedNode<V>(var value: V?, var next: String, var prev: String) : Serializable

class SetsStorage<V>(private val storage: DataBase<String, DoubleLinkedNode<V>>) : PersistentSetsStorage<V> {
    override fun createSet(id: String): Boolean {
        if (storage.read(id).get() != null)
            return false

        storage.write(id, DoubleLinkedNode(null, "", "")).get()
        return true
    }

    override fun deleteSet(id: String): Boolean {
        storage.delete(id)
        return true
    }

    override fun add(id: String, key: String, value: V): Boolean {
        val head = storage.read(id).get() ?: return false
        val firstNodeKey = head.next
        val firstNode = storage.read(firstNodeKey).get()
        val newNodeKey = id + key
        val newNode = DoubleLinkedNode(value, head.next, id)

        firstNode?.prev = newNodeKey
        head.next = newNodeKey

        if (firstNode != null) {
            storage.write(firstNodeKey, firstNode).get()
        }
        storage.write(id, head).get()
        storage.write(newNodeKey, newNode).get()
        return true
    }

    override fun remove(id: String, key: String): Boolean {
        val nodeKey = id + key
        val node = storage.read(nodeKey).get() ?: return false
        val prevNode = storage.read(node.prev).get()
        val nextNode = storage.read(node.next).get()

        prevNode?.next = node.next
        nextNode?.prev = node.prev

        if (prevNode != null)
            storage.write(node.prev, prevNode).get()
        if (nextNode != null)
            storage.write(node.next, nextNode).get()
        storage.delete(nodeKey).get()
        return true
    }

    override fun asSequence(id: String): Sequence<V> {
        val ls = mutableListOf<V>()
        var runner = storage.read(id).get() ?: return ls.asSequence()
        while (runner.next != "") {
            runner = storage.read(runner.next).get()!!
            ls.add(runner.value!!)
        }
        return ls.asSequence()
    }

    override fun get(id: String, key: String): V? {
        var runner = storage.read(id).get() ?: return null
        while (runner.next != "") {
            if (runner.next == key)
                return storage.read(runner.next).get()!!.value
            runner = storage.read(runner.next).get()!!
        }
        return null
    }

    override fun isSetExists(id: String): Boolean {
        return storage.read(id).get() != null
    }
}