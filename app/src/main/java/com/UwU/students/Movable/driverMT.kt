import kotlin.math.*
import kotlin.time.TimeSource
import kotlin.concurrent.thread
import kotlin.random.Random

val timeSource = TimeSource.Monotonic

interface Moveable {
    var coordX: Float
    var coordY: Float
    var speed: Float

    fun move(time: Float)
}

open class Human(
    private var fio: String,
    private var age: Int,
    override var speed: Float,
    override var coordX: Float,
    override var coordY: Float
) : Moveable {

    fun getFio() = fio      // тут я сам в шоке int to bool не подвезли а сахар добавили......
    fun setFio(value: String) { fio=value }

    fun getAge() = age
    fun setAge(value: Int) { age=value }

    open fun getHuman() {
        println("HUMAN $fio, Age: $age, current coords: (${ "%.2f".format(coordX) }, ${ "%.2f".format(coordY) }), speed: $speed")
    }

    override fun move(time: Float) {
        repeat(time.toInt()) {
            var distance = speed
            val dx: Float = ((Math.random() * 2 - 1) * distance).toFloat()
            val dy: Float = ((Math.random() * 2 - 1) * distance).toFloat()
            coordX += dx
            coordY += dy
        }
    }
}

class Driver(
    fio: String,
    age: Int,
    override var speed: Float,
    override var coordX: Float,
    override var coordY: Float,
    private var direction: Float
) : Human(fio, age, speed, coordX, coordY) {

    override fun move(time: Float) {
        val totalDistance = speed*time
        val dx = (cos(Math.toRadians(direction.toDouble()))*totalDistance).toFloat()
        val dy = (sin(Math.toRadians(direction.toDouble()))*totalDistance).toFloat()
        coordX += dx
        coordY += dy
    }

    override fun getHuman() {
        print("DRIVER ")
        super.getHuman()
    }
}

fun main() {
    val humans = arrayOf(
        Human("John Doe",25,2.0f,0.0f,0.0f),
        Human("Jane Smith",30,1.5f,5.0f,3.0f),
        Human("Bob Johnson",40,0.8f,-2.0f,4.0f),
        Human("Blex Arown",35,2.2f,3.0f,-1.0f)
    )

    val driver = Driver("Alex Driver",45, 3.0f, 10.0f, 10.0f, 45f)
    val simulationTime = 10f
    val timeStep = 1f

    println("Initial")
    humans.forEach {it.getHuman()}
    driver.getHuman()
    println()

    val startTime = timeSource.markNow()

    val humanThreads = humans.map{
        thread{
            var currentTime = 0f
            while (currentTime < simulationTime){
                it.move(timeStep)
                currentTime+=timeStep
            }
        }
    }

    val driverThread = thread{
        var currentTime = 0f
        while (currentTime < simulationTime){
            driver.move(timeStep)
            currentTime+=timeStep
        }
    }

    val displayThread = thread{
        var currentTime = 0f
        while (currentTime < simulationTime){
            println("\nTime: ${"%.1f".format(currentTime)}s")
            humans.forEach {it.getHuman()}
            driver.getHuman()
            println()
            currentTime+=timeStep
        }
    }

    humanThreads.forEach {it.join()}
    driverThread.join()
    displayThread.join()

    val endTime = timeSource.markNow()

    println("-------------------------------SIMULATION FINISH--------------------------------------")
    humans.forEach {it.getHuman()}
    driver.getHuman()
    println("TOTAL TIME TO EXECUTE: ${endTime-startTime}")
}
