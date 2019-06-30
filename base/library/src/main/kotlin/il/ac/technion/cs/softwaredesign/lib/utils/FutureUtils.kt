package il.ac.technion.cs.softwaredesign.lib.utils

import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture

/**
 * Compose a given CompletableFuture, forwarding the composed value of this future.
 * Use this when you want to compose small tasks which are independent of this CompletableFuture's value, and forward
 * the previous value for easy composing
 */
fun <T, U> CompletableFuture<T>.thenForward(otherFuture: CompletableFuture<U>) : CompletableFuture<T> {
    return thenCompose { value -> otherFuture.thenApply { value } }
}

/**
 * Compose a given block, forwarding the composed value of this future.
 * @see thenForward
 */
fun <T> CompletableFuture<T>.thenForward(block: (T)->Unit) : CompletableFuture<T> {
    return thenApply { value -> block(value); value }
}

/**
 * Apply a unit value to the last future, returning a CompletableFuture which contains a Unit
 */
fun <T> CompletableFuture<T>.thenDispose(): CompletableFuture<Unit> {
    return thenApply { }
}

fun <T, U> List<T>.mapComposeIndexList(transform: (i: Int, T) -> CompletableFuture<U>): CompletableFuture<Unit> {
    return if (this.isNotEmpty())
        this.mapIndexed { i, value -> transform(i, value) }
                .reduce { acc, completableFuture -> acc.thenCompose { completableFuture } }
                .thenApply { Unit }
    else ImmediateFuture { Unit }
}

fun <T, U> List<T>.mapComposeList(transform: (T) -> CompletableFuture<U>): CompletableFuture<Unit> {
    return if (this.isNotEmpty())
        this.map { value -> transform(value) }
                .reduce { acc, completableFuture -> acc.thenCompose { completableFuture } }
                .thenApply { Unit }
    else ImmediateFuture { Unit }
}

