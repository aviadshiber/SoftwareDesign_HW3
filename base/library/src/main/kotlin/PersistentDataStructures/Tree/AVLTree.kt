package PersistentDataStructures.Tree

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.io.*
import java.nio.charset.Charset
import kotlin.math.max

class AVLTree<Key : Comparable<Key>, Value>(private val storage: SecureStorage, id: String) : PersistentTree<Key, Value> {
    private val id = id.toByteArray(Charset.defaultCharset())

    private fun genKey(key: ByteArray): ByteArray {
        return id.plus(key)
    }

    private fun readRoot(): Node<Key, Value>? {
        return deserialize("root".toByteArray(Charset.defaultCharset()))
    }

    private fun serialize(n: Node<Key, Value>) {
        val os = ByteArrayOutputStream()
        ObjectOutputStream(os).writeObject(n)
        storage.write(genKey(n.id), os.toByteArray()).get()
        n.parent ?: storage.write(genKey("root".toByteArray(Charset.defaultCharset())), os.toByteArray()).get()
    }

    private fun deserialize(nodeId: ByteArray?): Node<Key, Value>? {
        nodeId ?: return null
        val bytes = storage.read(genKey(nodeId)).get()
        bytes ?: return null
        if (bytes.contentEquals("deleted".toByteArray(Charset.defaultCharset())))
            return null
        return ObjectInputStream(ByteArrayInputStream(bytes)).readObject() as Node<Key, Value>
    }

    private fun nextID(): ByteArray {
        val id = readLong("ID")
        writeLong("ID", id + 1)
        return id.toString().toByteArray(Charset.defaultCharset())
    }

    private fun readLong(name: String): Long {
        val rawKey = genKey(name.toByteArray(Charset.defaultCharset()))
        val rawData = storage.read(rawKey).get() ?: return 0
        val json = rawData.toString(Charset.defaultCharset())
        return Gson().fromJson(json)
    }

    private fun writeLong(name: String, num: Long) {
        val rawData = num.toString().toByteArray(Charset.defaultCharset())
        val rawKey = genKey(name.toByteArray(Charset.defaultCharset()))
        storage.write(rawKey, rawData).get()
    }

    private class Node<Key, Value>(var key: Key, var value: Value, var parent: ByteArray?, var id: ByteArray) : Serializable {
        companion object {
            private const val serialVersionUID = 42L
        }

        var balance: Int = 0
        var left: ByteArray? = null
        var right: ByteArray? = null
        var heightL = -1
        var heightR = -1

        fun height(): Int {
            return 1 + max(heightL, heightR)
        }
    }

    override fun add(key: Key, value: Value): Boolean {
        return set(key, value)
    }

    operator fun set(key: Key, value: Value): Boolean {
        val root = readRoot()
        //insert to empty tree
        if (root == null) {
            val newRoot = Node(key, value, null, nextID())
            serialize(newRoot)
            return true
        }

        var n: Node<Key, Value>? = root
        var parent: Node<Key, Value>
        while (true) {
            if (n!!.key == key) {
                n.value = value
                serialize(n)
                return false
            }
            parent = n
            val goLeft = n.key > key
            n = deserialize(if (goLeft) n.left else n.right)
            if (n == null) {
                val id = nextID()
                serialize(Node(key, value, parent.id, id))
                if (goLeft) {
                    parent.left = id
                    parent.heightL = 0
                } else {
                    parent.right = id
                    parent.heightR = 0
                }
                rebalance(parent)
                break
            }
        }
        return true
    }

    override operator fun get(key: Key): Value? {
        val root = readRoot()
        root ?: return null
        val closest = closestNode(key, root)
        return if (key == closest.key) closest.value else null
    }

    private fun closestNode(key: Key, node: Node<Key, Value>): Node<Key, Value> {
        if (key > node.key) {
            node.right ?: return node
            return closestNode(key, deserialize(node.right!!)!!)
        }
        if (key < node.key) {
            node.left ?: return node
            return closestNode(key, deserialize(node.left!!)!!)
        }
        return node
    }

    override fun delete(key: Key): Boolean {
        //no root no problem
        var root = readRoot()
        root ?: return false

        //find the node to remove
        var delNode: Node<Key, Value>? = root
        while (delNode != null) {
            if (delNode.key == key)
                break
            delNode = deserialize(if (key > delNode.key) delNode.right else delNode.left)
        }

        //key not in tree no problem
        delNode ?: return false

        //remove a leaf
        if (delNode.left == null && delNode.right == null) {
            //deleted root
            if (delNode.parent == null) {
                storage.write("root".toByteArray(Charset.defaultCharset()), "deleted".toByteArray(Charset.defaultCharset())).get()
                return true
            }

            //update deleted node's parent and rebalance
            val parent = deserialize(delNode.parent)!!
            if (parent.left?.contentEquals(delNode.id) == true) {
                parent.left = null
                parent.heightL = -1
            } else {
                parent.right = null
                parent.heightR = -1
            }
            rebalance(parent)
        }
        //delNode has at least one child replace delNode with a leaf
        else {
            val replacement = if (delNode.left != null) maxNode(deserialize(delNode.left)!!) else minNode(deserialize(delNode.right)!!)

            //replace keys and values
            delNode.key = replacement.key
            delNode.value = replacement.value

            val parent = if (replacement.parent!!.contentEquals(delNode.id)) delNode else deserialize(replacement.parent)!!
            if (parent.left?.contentEquals(replacement.id) == true) {
                parent.left = null
                parent.heightL = -1
            } else {
                parent.right = null
                parent.heightR = -1
            }
            serialize(delNode)
            rebalance(parent)
        }

        return true
    }

    private fun maxNode(node: Node<Key, Value>): Node<Key, Value> {
        var it = node
        while (it.right != null) {
            it = deserialize(it.right)!!
        }
        return it
    }

    private fun minNode(node: Node<Key, Value>): Node<Key, Value> {
        var it = node
        while (it.left != null) {
            it = deserialize(it.left)!!
        }
        return it
    }

    private fun rebalance(n: Node<Key, Value>) {
        updateBalanceValue(n)
        var nn = n
        if (nn.balance == -2) {
            val right = deserialize(nn.right)!!
            nn = if (height(deserialize(right.right)) >= height(deserialize(right.left))) rollL(nn) else rollRL(nn)
        } else {
            if (nn.balance == 2) {
                val left = deserialize(nn.left)!!
                nn = if (height(deserialize(left.left)) >= height(deserialize(left.right))) rollR(nn) else rollLR(nn)
            }
        }
        serialize(nn)
        if (nn.parent != null) {
            val parent = deserialize(nn.parent)!!
            if (parent.left?.contentEquals(nn.id) == true) {
                parent.heightL = nn.height()
            } else {
                parent.heightR = nn.height()
            }
            rebalance(parent)
        }
    }

    private fun rollL(a: Node<Key, Value>): Node<Key, Value> {
        val r: Node<Key, Value>? = deserialize(a.right)
        r!!.parent = a.parent
        a.right = r.left
        val rl = deserialize(a.right)
        if (rl != null) {
            rl.parent = a.id
            serialize(rl)
        }
        a.heightR = rl?.height() ?: -1
        r.left = a.id
        a.parent = r.id
        r.heightL = a.height()
        val parent = deserialize(r.parent)
        if (parent != null) {
            if (parent.right?.contentEquals(a.id) == true) {
                parent.right = r.id
                parent.heightR = r.height()
            } else {
                parent.left = r.id
                parent.heightL = r.height()
            }
            serialize(parent)
        }

        updateBalanceValue(a, r)
        return r
    }

    private fun rollR(a: Node<Key, Value>): Node<Key, Value> {
        val l: Node<Key, Value>? = deserialize(a.left)
        l!!.parent = a.parent
        a.left = l.right
        val lr = deserialize(a.left)
        if (lr != null) {
            lr.parent = a.id
            serialize(lr)
        }
        a.heightL = lr?.height() ?: -1
        l.right = a.id
        a.parent = l.id
        l.heightR = a.height()
        val parent = deserialize(l.parent)
        if (parent != null) {
            if (parent.right?.contentEquals(a.id) == true) {
                parent.heightR = l.height()
                parent.right = l.id
            } else {
                parent.left = l.id
                parent.heightL = l.height()
            }
            serialize(parent)
        }
        updateBalanceValue(a, l)
        return l
    }

    private fun rollLR(n: Node<Key, Value>): Node<Key, Value> {
        n.left = rollL(deserialize(n.left!!)!!).id
        return rollR(n)
    }

    private fun rollRL(n: Node<Key, Value>): Node<Key, Value> {
        n.right = rollR(deserialize(n.right!!)!!).id
        return rollL(n)
    }

    private fun height(n: Node<Key, Value>?): Int {
        if (n == null) return -1
        return n.height()
    }

    private fun updateBalanceValue(vararg nodes: Node<Key, Value>) {
        for (n in nodes) {
            n.balance = n.heightL - n.heightR
            serialize(n)
        }
    }

    override fun toString(): String {
        return toString(readRoot())

    }

    private fun toString(n: Node<Key, Value>?): String {
        if (n == null) {
            return ""
        }
        val str = StringBuilder()
        str.append(toString(deserialize(n.right)))
        str.append("${n.key},${n.value} ")
        str.append(toString(deserialize(n.left)))

        return str.toString()
    }

    override fun asSequence(): Sequence<Pair<Key, Value>> {
        return asSequence(readRoot())
    }

    private fun asSequence(node: Node<Key, Value>?): Sequence<Pair<Key, Value>> {
        node ?: return emptySequence()

        return asSequence(deserialize(node.right)) + Pair(node.key, node.value) + asSequence(deserialize(node.left))
    }

    override fun topN(n: Int): List<Pair<Key, Value>> {
        return topN(readRoot(), n).second.toList()
    }

    private fun topN(node: Node<Key, Value>?, n: Int): Pair<Int, Sequence<Pair<Key, Value>>> {
        if (n == 0 || node == null) {
            return Pair(n, emptySequence())
        }
        var res = emptySequence<Pair<Key, Value>>()

        val right = topN(deserialize(node.right), n)
        var temp = right.first
        res += right.second

        if (temp > 0) {
            temp -= 1
            res += Pair(node.key, node.value)
        }
        val left = topN(deserialize(node.left), temp)

        return Pair(left.first, res + left.second)
    }

    fun selfCheck(): Boolean {
        return verifyConnections(readRoot()) && verifyBalance(readRoot())
    }

    private fun verifyBalance(n: Node<Key, Value>?): Boolean {
        if (n != null) {
            if (!verifyBalance(deserialize(n.left))) return false
            val balance = n.balance
            val leftH = if (n.left == null) -1 else deserialize(n.left)!!.height()
            val rightH = if (n.right == null) -1 else deserialize(n.right)!!.height()
            if (leftH != n.heightL) {
                println("invalid leftH $leftH vs ${n.heightL}")
                return false
            }
            if (rightH != n.heightR) {
                println("invalid rightH ${rightH}vs ${n.heightR}")
                return false
            }
            if (balance != (leftH - rightH)) {
                println("node balance is incorrect $balance vs ${leftH - rightH}")
                return false
            }
            if (balance !in setOf(0, 1, -1)) {
                println("balance is not a valid value $balance")
                return false
            }

            if (!verifyBalance(deserialize(n.right))) return false
        }
        return true
    }

    private fun verifyConnections(n: Node<Key, Value>?): Boolean {
        if (n != null) {
            if (!verifyBalance(deserialize(n.left))) return false
            val l = deserialize(n.left)
            val r = deserialize(n.right)

            if (l != null && !l.parent!!.contentEquals(n.id)) {
                println("child not connected to father")
                return false
            }
            if (r != null && !r.parent!!.contentEquals(n.id)) {
                println("child not connected to father")
                return false
            }
            if (!verifyBalance(deserialize(n.right))) return false
        }
        return true
    }

}