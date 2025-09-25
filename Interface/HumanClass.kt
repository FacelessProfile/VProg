package Interface
import kotlin.random.Random

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