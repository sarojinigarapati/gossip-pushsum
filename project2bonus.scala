import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import scala.util.Random
import scala.concurrent.duration._
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable._

object project2bonus {
	def main(args: Array[String]){
		val GOSSIP_MAX: Int = 20
		val PUSH_SUM_MAX: Int = 3
		val msg: String = "Gossip"

		case class CreateTopology() 
		case class Gossip(msg: String)
		case class PushSum(s:Double, w:Double)
		case class PushSumDone(sum: Double)
		case class GotGossip(id: String)

		println("project2 - Gossip Simulator ")
		class Master(num: Int, topology: String, algorithm: String) extends Actor{

			var converged: Boolean = false
			var startTime: Long = _
			var numNodes: Int = _
			var pivot: String = _
			var gossipNodes: Int = _
			var gossipList: ArrayBuffer[String] = new ArrayBuffer[String]
			var failedMap = Map[String, String]()
			// var failedList: ArrayBuffer[String] = new ArrayBuffer[String]

			def receive = {
				case CreateTopology() =>
					numNodes = num
					if(topology == "full" || topology == "line"){
						pivot = (Random.nextInt(numNodes)).toString
						println("Randomly kill 1% of the nodes in the topology")
						var failedCount: Int = (numNodes)/100
						for(i <- 0 until failedCount){
							var node: String = Random.nextInt(numNodes).toString
							if(node != pivot) failedMap += (node -> "failed")
						}
						for(i <- 0 until numNodes){
							var coordinates: Array[Int] = Array(i,0,0)
							var myID: String = i.toString
							// println(myID)
							val act = context.actorOf(Props(new Node(coordinates, myID, numNodes, topology, algorithm, i+1, failedMap)),name=myID)
						}
						startTime = System.currentTimeMillis
					} 
					if(topology == "3D" || topology == "imp3D"){
						var size: Int = Math.cbrt(numNodes).toInt
						numNodes = size*size*size
						var count = 0
						for(i <- 0 until size){
							for(j <- 0 until size){
								for(k <- 0 until size){
									count += 1
									var coordinates: Array[Int] = Array(i,j,k)
									var myID: String = convert(i,j,k)
									// println(count)
									val act = context.actorOf(Props(new Node(coordinates, myID, numNodes, topology, algorithm, count, failedMap)),name=myID)
									act ! "GetNeighbors"
								}
							}
						}
						startTime = System.currentTimeMillis
						pivot = convert(Random.nextInt(size),Random.nextInt(size),Random.nextInt(size))
						println("Randomly kill 1% of the nodes in the topology")
						var failedCount: Int = (numNodes)/100
						for(i <- 0 until failedCount){
							var x: Int = Random.nextInt(size)
							var y: Int = Random.nextInt(size)
							var z: Int = Random.nextInt(size)
							var node: String = convert(x,y,z)
							if(node != pivot) failedMap += (node -> "failed")
						}
					} 
					println("Starting the algorithm from node = " + pivot)

					if(algorithm == "gossip"){
						context.actorSelection(pivot) ! Gossip(msg)
					} else if(algorithm == "push-sum") {
						context.actorSelection(pivot) ! PushSum(0,1)
					}

				case "Done" =>
					var totalTime: Long = System.currentTimeMillis - startTime
					println("****** RESULT ******")
					println("Time to converge for the gossip algorithm = " + totalTime.millis)
					println("Number of nodes which received the rumour atleast once = " + gossipNodes)
					println("****** END ******")
					context.system.shutdown()

				case PushSumDone(sum: Double) =>
					var totalTime: Long = System.currentTimeMillis - startTime
					println("****** RESULT ******")
					println("Time to converge for the push-sum algorithm = " + totalTime.millis)
					println("Sum of the node values in the topology = " + sum)
					println("****** END ******")
					context.system.shutdown()

				case GotGossip(id: String) =>
					gossipNodes += 1
					gossipList.append(id)

			}
		}

		class Node(n: Array[Int], myID: String, numNodes: Int, topology: String, algorithm: String, sum: Int, failedMap: Map[String, String]) 
			extends Actor{

			var count: Int = _
			var message: String = _
			var s: Double = sum.toDouble  
			var w : Double = 0.0
			var pcount : Int = _
			var next: String = _ 
			var neighborList: ArrayBuffer[String] = new ArrayBuffer[String]
			var atleastOnce: Boolean = false


			def getNext(): String = {
				
				topology match {
					case "full" =>
						var next: String = (Random.nextInt(numNodes)).toString
						while(failedMap.contains(next)) next = (Random.nextInt(numNodes)).toString
						return next
					case "line" =>
						var x: Int = myID.toInt
						if(x == 0){
							return (x+1).toString
						} else if(x == numNodes-1) {
							return (x-1).toString
						} else {
							var ran: Int = Random.nextInt(2)
							if(ran == 0) return (x-1).toString
							else return (x+1).toString
						}
					case "3D" =>
						var i:Int = Random.nextInt(neighborList.size)
						var next: String = neighborList(i)
						while(failedMap.contains(next)) next = neighborList(i)
						return next
					case "imp3D" =>
						var i:Int = Random.nextInt(neighborList.size)
						var next: String = neighborList(i)
						while(failedMap.contains(next)) next = neighborList(i)
						return next
				}
			}

			def receive = {
				case Gossip(msg: String) =>
					if(atleastOnce == false){
						context.parent ! GotGossip(myID)
						atleastOnce = true
					}
					// println("Got msg for node = " + myID)
					// Randomly select a neighbor and send the gossip
					if(count <= GOSSIP_MAX-1){
						this.message = msg
						count += 1
						var next: String = getNext()
						while(next == myID) next = getNext()
						// println("Sent msg to node = " + next)
						context.actorSelection("../"+next) ! Gossip(msg)
					} else if(count == GOSSIP_MAX) {
						context.parent ! "Done"
					} 
				case PushSum(s: Double, w: Double) =>

					if(Math.abs((this.s/this.w)- ((this.s+s)/(this.w+w))) <=1E-10){
						pcount += 1
					} else {
						pcount = 0
					}
					if(pcount == PUSH_SUM_MAX){
						context.parent ! PushSumDone(this.s/this.w)
					} else {
						this.s = this.s + s
						this.w = this.w + w
						this.s = this.s/2
						this.w = this.w/2
						var next : String = getNext()
						while(next == myID) next = getNext()
						context.actorSelection("../"+next) ! PushSum(this.s,this.w)
					}

				case "GetNeighbors" =>
					var size: Int = Math.cbrt(numNodes).toInt
					if(n(0)-1 >= 0) neighborList.append(convert(n(0)-1,n(1),n(2)))
					if(n(0)+1 < size) neighborList.append(convert(n(0)+1,n(1),n(2)))
					if(n(1)-1 >= 0) neighborList.append(convert(n(0),n(1)-1,n(2)))
					if(n(1)+1 < size) neighborList.append(convert(n(0),n(1)+1,n(2)))
					if(n(2)-1 >= 0) neighborList.append(convert(n(0),n(1),n(2)-1))
					if(n(2)+1 < size) neighborList.append(convert(n(0),n(1),n(2)+1))
					if(topology == "imp3D"){
						var x: Int = Random.nextInt(size)
						var y: Int = Random.nextInt(size)
						var z: Int = Random.nextInt(size)
						neighborList.append(convert(x,y,z))
					}
			}
		}

		def convert(x:Int, y:Int, z:Int): String = {
			return ((((x.toString).concat(".")).concat(y.toString)).concat(".")).concat(z.toString)

		}

		if(args.size<3){
			println("Enter valid number of inputs!!")
		} else {
			val system = ActorSystem("MasterSystem")
			val master = system.actorOf(Props(new Master(args(0).toInt, args(1), args(2))), name = "master")
			master ! CreateTopology()
		}

	}	
}