package de.lolhens.fs2.utils

import cats.effect.IO
import fs2.{Chunk, Stream}

import scala.util.Random
import Fs2Utils._
import cats.effect.std.Queue
import cats.syntax.parallel._

import scala.concurrent.duration._

class Fs2UtilsOomSuite extends CatsEffectSuite {
  override def munitTimeout: Duration = 10.minutes

  test("filter should not oom") {
    bigStream
      .filter(_ > 128)
      .compile
      .drain
  }

  test("dupe should not oom") {
    bigStream
      .dupe()
      .evalMap {
        case (a, b) =>
          for {
            hashInfo12 <- compileHashInfo(a).parProduct(compileHashInfo(b))
          } yield {
            println("finished")
            assertEquals(hashInfo12._1, hashInfo12._2)
          }
      }
      .compile
      .drain
  }

  test("extract should not oom") {
    bigStream
      .extract(_.size.compile.lastOrError)
      .evalMap {
        case (stream, expectedSizeIO) =>
          for {
            size <- stream.size.compile.lastOrError
            expectedSize <- expectedSizeIO
          } yield {
            println("finished")
            assertEquals(size, expectedSize)
          }
      }
      .compile
      .drain
  }

  test("splitAt should not oom") {
    val limit = 1_000_000
    bigStream
      .splitAt(limit)
      .evalMap {
        case (a, b) =>
          for {
            _ <- a.compile.drain
            _ = println("stream a consumed")
            _ <- b.compile.drain
          } yield {
            println("finished")
          }
      }
      .compile
      .drain
  }

  test("start should not oom") {
    for {
      queue <- Queue.bounded[IO, Unit](1)
      _ <- (Stream.exec(queue.offer(())) ++ bigStream)
        .start()
        .evalMap { stream =>
          for {
            _ <- queue.take
            _ <- stream.compile.drain
          } yield {
            println("finished")
          }
        }
        .compile
        .drain
    } yield ()
  }
}
