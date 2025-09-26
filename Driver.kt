open class Human(
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
    
    open fun getHuman(){
        println("HUMAN $Fio, Age: $Age, current coords: ($CoordX,$CoordY), speed: $Speed ")
    }
    /*
    fun getFio(): String{
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
    }*/
	open fun move(time: Float) {		//Random Walk Linear
        var cons = time
        while (cons>0){
    	val distance: Float = Speed //1 due to cycle
    	val dx: Float = ((Math.random() * 2 - 1) * distance).toFloat()
    	val dy: Float = ((Math.random() * 2 - 1) * distance).toFloat()
    	CoordX += dx
    	CoordY += dy
        cons-=1
        }
	}
    
}


class PILOT(
  FIO: String,
  AGE: Int,
  Speed: Float,
  CoordX: Float,
  CoordY: Float
) : Human(FIO,AGE,Speed,CoordX,CoordY){
    override fun move(time : Float){
        var cons = time
        while (cons>0){
    	val distance: Float = Speed * 1 //1 due to cycle
        if(Math.random() >= 0.51){ 
         CoordX += distance
        }
    	else{
        CoordY += distance
        }
        cons-=1
    }
    
}
}
fun main() {
    val human1 = Human("John Doe", 50, 5.0f,0.0f,0.0f)
    human1.getHuman()
    human1.move(15f)
    human1.getHuman()
	val pilot1 = PILOT("Alexandoor", 50, 700f,0.0f,0.0f)
    pilot1.getHuman()
    pilot1.move(15f)
    pilot1.getHuman()
    //human1.getHuman()
}
