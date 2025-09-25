package Interface

interface Moveable {
    var coordX: Float
    var coordY: Float
    var speed: Float

    fun move(time: Float)
}
