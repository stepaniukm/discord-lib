package example

import scala.concurrent.{ExecutionContext, Future}
import cats._
import cats.implicits._
import cats.effect.{IO, Async}

import ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

object Hello extends App {
  val future = Future {
    println("I'm future");

    "Coś";
  }

  val unitF = for {
    a <- future
    b <- future
  } yield (a + b);

  unitF map println
}
