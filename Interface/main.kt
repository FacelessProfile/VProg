package Interface

import kotlin.concurrent.thread
import kotlin.time.TimeSource

val timeSource = TimeSource.Monotonic

fun main() {
    val humans =arrayOf(
        Human("John Doe", 25, 2.0f, 0.0f, 0.0f),
        Human("Jane Smith", 30, 1.5f, 5.0f, 3.0f),
        Human("Bob Johnson", 40, 0.8f, -2.0f, 4.0f),
        Human("Blex Arown", 35, 2.2f, 3.0f, -1.0f)
    )

    val driver=Driver("Alex Driver", 45, 3.0f, 10.0f, 10.0f, 45f)
    val simulationTime = 10f
    val timeStep = 1f

    println("Initial")
    humans.forEach {it.getHuman()}
    driver.getHuman()
    println()

    val startTime = timeSource.markNow()

    val humanThreads=humans.map{
        thread {
            var currentTime = 0f
            while (currentTime<simulationTime){
                it.move(timeStep)
                currentTime += timeStep
            }
        }
    }

    val driverThread =thread{
        var currentTime = 0f
        while (currentTime<simulationTime){
            driver.move(timeStep)
            currentTime += timeStep
        }
    }

    val displayThread =thread{
        var currentTime = 0f
        while (currentTime<simulationTime){
            println("\nTime: ${"%.1f".format(currentTime)}s")
            humans.forEach {it.getHuman()}
            driver.getHuman()
            println()
            currentTime += timeStep
        }
    }

    humanThreads.forEach {it.join()}
    driverThread.join()
    displayThread.join()

    val endTime = timeSource.markNow()

    println("-------------------------------SIMULATION FINISH--------------------------------------")
    humans.forEach {it.getHuman()}
    driver.getHuman()
    println("TOTAL TIME TO EXECUTE: ${endTime - startTime}")

}
