import kotlin.random.Random
import kotlin.math.sqrt
import kotlin.time.TimeSource
val timeSource = TimeSource.Monotonic
open class Human(
    private var fio: String,
    private var age: Int,
    private var speed: Float,
    private var coordX: Float,
    private var coordY: Float
) {
    // Геттеры и сеттеры
    fun getFio(): String = fio
    fun setFio(value: String) { fio = value }
    
    fun getAge(): Int = age
    fun setAge(value: Int) { age = value }
    
    fun getSpeed(): Float = speed
    fun setSpeed(value: Float) { speed = value }
    
    fun getCoordX(): Float = coordX
    fun setCoordX(value: Float) { coordX = value }
    
    fun getCoordY(): Float = coordY
    fun setCoordY(value: Float) { coordY = value }

    open fun getHuman() {
        println("HUMAN $fio, Age: $age, current coords: (${"%.2f".format(coordX)},${"%.2f".format(coordY)}), speed: $speed")
    }

    open fun move(time: Float) {
        // Random Walk - корректная реализация с учетом времени
        val totalDistance = speed * time
        val angle = Math.random() * 2 * Math.PI
        
        val dx = (Math.cos(angle) * totalDistance).toFloat()
        val dy = (Math.sin(angle) * totalDistance).toFloat()
        
        coordX += dx
        coordY += dy
    }
}

class Driver(
    fio: String,
    age: Int,
    speed: Float,
    coordX: Float,
    coordY: Float,
    direction: Float
) : Human(fio, age, speed, coordX, coordY) {
    
    var direction = 90.0f
    override fun move(time: Float) {
        val totalDistance = getSpeed() * time
        
        val dx = (Math.cos(Math.toRadians(direction.toDouble())) * totalDistance).toFloat()
        val dy = (Math.sin(Math.toRadians(direction.toDouble())) * totalDistance).toFloat()
        
        setCoordX(getCoordX() + dx)
        setCoordY(getCoordY() + dy)
    }
    
    override fun getHuman() {
        print("DRIVER ")
        super.getHuman()
    }
}

fun main() {
    val humans = arrayOf(
        Human("John Doe", 25, 2.0f, 0.0f, 0.0f),
        Human("Jane Smith", 30, 1.5f, 5.0f, 3.0f),
        Human("Bob Johnson", 40, 0.8f, -2.0f, 4.0f),
        Human("Blex Arown", 35, 2.2f, 3.0f, -1.0f)
    )
    
    val driver = Driver("Alex Driver", 45, 3.0f, 10.0f, 10.0f, 45f)
    val simulationTime = 10f
    val timeStep = 1f
    
    humans.forEach { it.getHuman() } //initial pos
    driver.getHuman()
    println()
    
    var currentTime = 0f
    val oneThread1 = timeSource.markNow()
    while (currentTime < simulationTime) {
        println("simulation: ${"%.1f".format(currentTime)}s")
        
        humans.forEach { 
            it.move(timeStep)
            it.getHuman()
        }
        
        driver.move(timeStep)
        driver.getHuman()
        println()
        
        currentTime += timeStep
    }
    
    println("-------------------------------SIMULATION FINISH--------------------------------------")
    humans.forEach { it.getHuman() }
    driver.getHuman()
    val oneThread2 = timeSource.markNow()
    println("TOTAL TIME TO EXECUTE: ${oneThread2-oneThread1}")
}