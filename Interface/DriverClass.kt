package Interface
import kotlin.math.*

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