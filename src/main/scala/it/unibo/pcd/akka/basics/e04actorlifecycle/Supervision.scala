package it.unibo.pcd.akka.basics.e04actorlifecycle

import akka.actor.typed.{ActorSystem, Behavior, SupervisorStrategy, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object SupervisedActor:
  def apply(supervisorStrategy: SupervisorStrategy): Behavior[String] = Behaviors
    .supervise[String](actor("")) // Behaviour che deve essere supervisionato
    .onFailure[RuntimeException](supervisorStrategy) // Cosa fare in caso di failure

  def actor(prefix: String): Behavior[String] = Behaviors.receive {
    case (ctx, "fail") => throw new RuntimeException("Just fail")
    case (ctx, "quit") =>
      ctx.log.info("Quitting")
      ctx.log.info(s"Bye!! $prefix")
      Behaviors.stopped
    case (ctx, s) =>
      ctx.log.info(s"Got ${prefix + s}")
      actor(prefix + s)
  }

object SupervisionExampleRestart extends App:
  // Qui come strategia per gestire le eccezioni faccio ripartire l'attore.
  // Lui gestisce foo bar poi fail fallisce, grazie alla gestione in restart, l'attore viene restartato
  // e continua da !!!, però in tal caso perdo lo stato (per mantenerlo basta specificare resume invece di restart), poi
  // riceverà di nuovo fail, fallirà, verrà restartato e poi riceve quit e termina gracefully.
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.restart), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleResume extends App:
  // Restart con mantenimento dello stato.
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.resume), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

object SupervisionExampleStop extends App:
  val system = ActorSystem[String](SupervisedActor(SupervisorStrategy.stop), "supervision")
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd


// Questo nel caso in cui volessi gestire i messaggi quando il figlio termina
// filio muore, il padre rimane attivo
object SupervisionExampleParent extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      Behaviors.receiveMessage { msg =>
        child ! msg
        Behaviors.same
      }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

// Se invece il parent fa il watch, non avendo gestito l'eccezione allora si stopperà anche lui, morirà assieme al figlio
object SupervisionExampleParentWatching extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      ctx.watch(child) // watching child (if Terminated not handled => dead pact)
      Behaviors.receiveMessage[String] { msg =>
        child ! msg
        Behaviors.same
      }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd

// Così riesco invece ad osservare e gestire la terminazione del figlio.
object SupervisionExampleParentWatchingHandled extends App:
  val system = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val child = ctx.spawn(SupervisedActor(SupervisorStrategy.stop), "fallibleChild")
      ctx.watch(child)
      Behaviors
        .receiveMessage[String] { msg =>
          child ! msg
          Behaviors.same
        }
        .receiveSignal { case (ctx, Terminated(ref)) =>
          ctx.log.info(s"Child ${ref.path} terminated")
          Behaviors.ignore
        }
    },
    "supervision"
  )
  for (cmd <- List("foo", "bar", "fail", "!!!", "fail", "quit")) system ! cmd
