package it.unibo.pcd.akka.basics.e01hello

import akka.actor.typed.{ActorSystem, Behavior, Signal}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import Counter.*

// "Actor" module definition
// Senza stato come definito da Akka
object Counter:
  enum Command: // APIs i.e. message that actors should received / send
    case Tick
  export Command.*
  def apply(from: Int, to: Int): Behavior[Command] =
    Behaviors.receive { (context, msg) =>
      msg match {
        case Tick if from != to =>
          context.log.info(s"Count: $from")
          Counter(from - from.compareTo(to), to)
        case _ => Behaviors.stopped
      }
    }
  def apply(to: Int): Behavior[Command] =
    Behaviors.setup(new Counter(_, 0, to))

class Counter(context: ActorContext[Counter.Command], var from: Int, val to: Int)
    extends AbstractBehavior[Counter.Command](context):
  override def onMessage(msg: Counter.Command): Behavior[Counter.Command] = msg match {
    case Tick if from != to =>
      context.log.info(s"Count: $from")
      from -= from.compareTo(to)
      this
    case _ => Behaviors.stopped
  }
@main def functionalApi: Unit =
  val system = ActorSystem[Command](Counter(0, 2), "counter")
  for (i <- 0 to 2) system ! Tick

@main def OOPApi: Unit =
  val system = ActorSystem[Counter.Command](Counter(2), "counter")
  for (i <- 0 to 2) system ! Tick
