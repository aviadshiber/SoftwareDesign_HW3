import PersistentDataStructures.Tree.AVLTree
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.github.vjames19.futures.jdk8.Future
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.random.Random

class TreeTest {
    private var tree: AVLTree<Double, Double>? = null

    @BeforeEach
    fun resetStore() {

        val store = object : SecureStorage {
            inner class ByteWrapper(private val byte: ByteArray) {
                override fun equals(other: Any?): Boolean {
                    if (other is ByteWrapper)
                        return byte contentEquals other.byte
                    return false
                }

                override fun hashCode(): Int = Arrays.hashCode(byte)
            }

            val back: MutableMap<ByteWrapper, ByteArray> = java.util.HashMap()
            override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
                return Future { back[ByteWrapper(key)] }

            }

            override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
                return Future { back[ByteWrapper(key)] = value }
            }
        }
        tree = AVLTree(store, "1")
    }

    @AfterEach
    fun assertTreeIsValid() {
        Assertions.assertTrue(tree!!.selfCheck())
    }

    @Test
    fun `insert returns true if a key was added`() {

        Assertions.assertTrue(tree!!.set(1.0, 2.0))
    }

    @Test
    fun `insert returns false if we updated a value of present key`() {
        tree!![1.0] = 2.0

        Assertions.assertFalse(tree!!.set(1.0, 10.0))
    }

    @Test
    fun `if a key was inserted we can get it's value`() {
        tree!![1.0] = 2.0

        Assertions.assertTrue(tree!![1.0] == 2.0)
    }

    @Test
    fun `if a key was not inserted get will return null`() {
        tree!![1.0] = 2.0

        Assertions.assertNull(tree!![2.0])
    }

    @Test
    fun `if we insert multiple keys they will all be in the tree`() {
        val numIter = 20
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 1..numIter) {
            val key = Random.nextDouble()
            val value = i * i.toDouble()
            tree!![key] = value
            list.add(Pair(key, value))
        }
        list.forEach { Assertions.assertTrue(tree!![it.first] == it.second) }
    }

    @Test
    fun `asSequence return all key-value pairs sorted in descending order by key`() {
        val numIter = 20
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 1..numIter) {
            val key = Random.nextDouble()
            val value = i * i.toDouble()
            tree!![key] = value
            list.add(Pair(key, value))
        }

        list.sortByDescending { it.first }

        val pairs = tree!!.asSequence().toList()

        Assertions.assertTrue(list.size == pairs.size)

        for ((expected, actual) in list.zip(pairs)) {
            Assertions.assertTrue(expected.first == actual.first)
            Assertions.assertTrue(expected.second == actual.second)
        }
    }

    @Test
    fun `topN return the N largest key-value pairs sorted in descending order by key`() {
        val numIter = 20
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 1..numIter) {
            val key = Random.nextDouble()
            val value = i * i.toDouble()
            tree!![key] = value
            list.add(Pair(key, value))
        }

        list.sortByDescending { it.first }
        val expected10 = list.take(10)
        val pairs = tree!!.topN(10).toList()

        Assertions.assertTrue(expected10.size == pairs.size)

        for ((expected, actual) in expected10.zip(pairs)) {
            Assertions.assertTrue(expected.first == actual.first)
            Assertions.assertTrue(expected.second == actual.second)
        }
    }

    @Test
    fun `topN will return a shorter sequence if there are less then N keys`() {
        val numIter = 20
        val list = mutableListOf<Pair<Double, Double>>()
        for (i in 1..numIter) {
            val key = Random.nextDouble()
            val value = i * i.toDouble()
            tree!![key] = value
            list.add(Pair(key, value))
        }

        list.sortByDescending { it.first }
        val expected10 = list.take(10)
        val pairs = tree!!.topN(100).toList()

        Assertions.assertTrue(list.size == pairs.size)

        for ((expected, actual) in expected10.zip(pairs)) {
            Assertions.assertTrue(expected.first == actual.first)
            Assertions.assertTrue(expected.second == actual.second)
        }
    }

    @Test
    fun `delete does nothing if key is not present`() {
        tree!![1.0] = 2.0
        tree!![3.0] = 2.0
        val expected = tree!!.asSequence().toList()
        tree!!.delete(14.0)

        val actual = tree!!.asSequence().toList()
        Assertions.assertTrue(expected.size == actual.size)

        for ((e, a) in expected.zip(actual)) {
            Assertions.assertTrue(e.first == a.first)
            Assertions.assertTrue(e.second == a.second)
        }
    }

    @Test
    fun `delete remove a key if it's in the key`() {
        for (j in 0..20) {
            val keys = listOf(1.0, 2.0, 3.0, 4.0, 0.0).shuffled()

            for (i in keys) {
                tree!![i] = i
            }
            val deleteOrder = keys.shuffled()
            for (i in deleteOrder) {
                assertTreeIsValid()
                tree!!.delete(i)
                assertTreeIsValid()
                Assertions.assertNull(tree!![i])
            }

        }
    }
}