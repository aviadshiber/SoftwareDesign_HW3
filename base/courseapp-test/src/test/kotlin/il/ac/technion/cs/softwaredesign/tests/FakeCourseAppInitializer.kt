package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.CourseAppInitializer
import io.github.vjames19.futures.jdk8.ImmediateFuture
import java.util.concurrent.CompletableFuture

class FakeCourseAppInitializer : CourseAppInitializer {
    override fun setup(): CompletableFuture<Unit> = ImmediateFuture { }

}