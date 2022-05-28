package it.unibo.pcd.akka.basics.e06interaction

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Success

object HelloBehavior:
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])
  def apply(): Behavior[Greet] = Behaviors.receive { (context, message) =>
    context.log.info("Hello {}!", message.whom)
    message.replyTo ! Greeted(message.whom, context.self)
    Behaviors.same
  }

object InteractionPatternsAsk extends App:
  import HelloBehavior.*

  val system = ActorSystem(
    Behaviors.setup[Greeted] { ctx =>
      val greeter = ctx.spawnAnonymous(HelloBehavior())
      given Timeout = 2.seconds
      given Scheduler = ctx.system.scheduler
      given ExecutionContext = ctx.executionContext
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo))
      f.onComplete { // dentro qui non posso accedere al contest (per esempio per fare loggin) in quanto non è thread-safe
            // da utilizzare solo dentro receive o setup.
        case Success(Greeted(who, from)) => println(s"$who has been greeted by ${from.path}!")
        case _ => println("No greet")
      }
      Behaviors.empty
    },
    name = "hello-world"
  )

object InteractionPatternsPipeToSelf extends App:
  import HelloBehavior._

  val system = ActorSystem(
    Behaviors.setup[Greeted] { ctx =>
      val greeter = ctx.spawn(HelloBehavior(), "greeter")
      given Timeout = 2.seconds
      given Scheduler = ctx.system.scheduler
      val f: Future[Greeted] = greeter ? (replyTo => Greet("Bob", replyTo))
      ctx.pipeToSelf(f)(_.getOrElse(Greeted("nobody", ctx.system.ignoreRef))) // Il greeted interno è per il caso in cui la Future fallisce
      Behaviors.receive { case (ctx, Greeted(whom, from)) =>
        ctx.log.info(s"$whom has been greeted by ${from.path.name}")
        Behaviors.stopped
      }
    },
    name = "hello-world"
  )

object InteractionPatternsSelfMessage extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      Behaviors.withTimers { timers =>
        Behaviors.receiveMessage {
          case "" => Behaviors.stopped
          case s =>
            ctx.log.info("" + s.head)
            timers.startSingleTimer(s.tail, 300.millis)
            Behaviors.same
        }
      }
    },
    name = "hello-world"
  )

  system ! "hello akka"

object InteractionPatternsMsgAdapter extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      // Adatto questo riferimento (che gestirebbe Stringhe) a gestire interi, specificando un opportuna conversione
      val adaptedRef: ActorRef[Int] = ctx.messageAdapter[Int](i => if (i == 0) "" else i.toString)
      adaptedRef ! 130
      adaptedRef ! 0
      Behaviors.receiveMessage {
        case "" =>
          ctx.log.info("Bye bye")
          Behaviors.stopped
        case s =>
          ctx.log.info(s)
          Behaviors.same
      }
    },
    name = "hello-world"
  )

  system ! "hello akka"
