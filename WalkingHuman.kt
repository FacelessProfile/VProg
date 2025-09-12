class Human(
FIO:String,
AGE: Int,
speed: Float,
coordX: Float,
coordY: Float
){
    var Fio: String = FIO 
    var Age: Int =  AGE
    var Speed: Float = speed
    var CoordX: Float = coordX
    var CoordY: Float = coordY
    
    fun getHuman(){
        println("HUMAN $Fio, Age: $Age, current coords: ($CoordX,$CoordY), speed: $Speed ")
    }
    
    /*fun getFio(): String{
        return this.Fio.toString()
    }
    
    fun getAge(): Int{
        return this.Age
    }
    
    fun getSpeed(): Float{
        return this.Speed
    }
    
    fun setFio(newFio:String){
        this.Fio = newFio
    }
    
    fun setAge(newAge: Int){
        this.Age = newAge
    }
    
    fun setSpeed(newSpeed:Float){
        this.Speed = newSpeed
    }
    fun setCorX(newCorX:Float){
        this.CoordX = newCorX
    }
    fun setCorY(newCorY:Float){
        this.CoordY = newCorY
    }
	*/
	fun move(time: Float) {                //Random Walk Linear
    	val distance: Float = Speed * time
    	val dx: Float = ((Math.random() * 2 - 1) * distance).toFloat()
    	val dy: Float = ((Math.random() * 2 - 1) * distance).toFloat()
    	CoordX += dx
    	CoordY += dy
	}
    
}

fun main() {
    val human1 = Human("John Doe", 50, 5.0f,0.0f,0.0f)
    human1.getHuman()
    var cons = 50
    while (cons>0){
    human1.move(15f)
    human1.getHuman()
    cons-=1
    }
    //human1.getHuman()
}
