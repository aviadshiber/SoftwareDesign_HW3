package il.ac.technion.cs.softwaredesign

import java.util.concurrent.CompletableFuture

interface DataBase<KeyType, ValueType> {
    /**
     * retrieves the entry associated with [key] from the database
     *
     * @return entry of [key] if [key] is in the database ,else null
     */
    fun read(key: KeyType): CompletableFuture<ValueType?>

    /**
     * associates [key] with [value], storing them in the database
     */
    fun write(key: KeyType, value: ValueType): CompletableFuture<Unit>

    /**
     * deletes entry associated with [key]
     * after this read([key]) will return null
     * but the key may be reused again for another entry
     */
    fun delete(key: KeyType): CompletableFuture<Unit>
}