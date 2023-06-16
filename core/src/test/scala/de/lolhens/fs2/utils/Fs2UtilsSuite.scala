package de.lolhens.fs2.utils

import cats.effect.{Deferred, IO}
import fs2.Stream
import Fs2Utils._
import cats.effect.std.Queue
import cats.syntax.parallel._

import scala.concurrent.duration._

class Fs2UtilsSuite extends CatsEffectSuite {
  override def munitTimeout: Duration = 1.second

  test("dupe") {
    Stream.chunk(bytesChunk)
      .covary[IO]
      .dupe()
      .evalMap {
        case (a, b) =>
          for {
            abChunk <- a.chunkAll.compile.lastOrError
              .parProduct(b.chunkAll.compile.lastOrError)
          } yield {
            println("assert")
            assertEquals(abChunk._1, bytesChunk)
            assertEquals(abChunk._2, bytesChunk)
          }
      }
      .compile
      .drain
  }

  test("extract") {
    Stream.chunk(bytesChunk)
      .covary[IO]
      .extract(_.size.compile.lastOrError)
      .evalMap {
        case (stream, sizeIO) =>
          for {
            chunk <- stream.chunkAll.compile.lastOrError
            size <- sizeIO
          } yield {
            println("assert")
            assertEquals(chunk, bytesChunk)
            assertEquals(size, bytesChunk.size.toLong)
          }
      }
      .compile
      .drain
  }

  test("splitAt") {
    val limit = 1_000
    Stream.chunk(bytesChunk)
      .covary[IO]
      .splitAt(limit)
      .evalMap {
        case (a, b) =>
          for {
            aChunk <- a.chunkAll.compile.lastOrError
            bChunk <- b.chunkAll.compile.lastOrError
          } yield {
            println("assert")
            assertEquals(aChunk, bytesChunk.take(limit))
            assertEquals(bChunk, bytesChunk.drop(limit))
          }
      }
      .compile
      .drain
  }

  test("start") {
    for {
      queue <- Queue.bounded[IO, Unit](1)
      _ <- (Stream.exec(queue.offer(())) ++ Stream.chunk(bytesChunk))
        .covary[IO]
        .start()
        .evalMap { stream =>
          for {
            _ <- queue.take
            chunk <- stream.chunkAll.compile.lastOrError
          } yield {
            println("assert")
            assertEquals(chunk, bytesChunk)
          }
        }
        .compile
        .drain
    } yield ()
  }

  test("start should not let you consume more than once".fail) {
    val iterator = List(bytesChunk).iterator
    Stream.eval(IO(iterator.next()))
      .unchunks
      .start()
      .evalMap { stream =>
        stream.compile.drain.as(stream)
      }
      .compile
      .lastOrError
      .flatMap { stream =>
        stream
          .chunkAll
          .compile
          .lastOrError
          .map { testChunk =>
            println("assert")
            assertEquals(testChunk, bytesChunk)
          }
      }
  }

  test("memoize should emit on empty stream") {
    Stream.empty
      .covary[IO]
      .memoize
      .evalMap(_.compile.drain)
      .compile
      .lastOrError
  }

  test("memoize should emit immediately") {
    Deferred[IO, Unit].flatMap { deferred =>
      Stream.eval(deferred.get)
        .covary[IO]
        .memoize
        .evalMap(stream =>
          deferred.complete(()) >>
            stream.compile.drain
        )
        .compile
        .lastOrError
    }
  }

  test("memoize should let you consume more than once") {
    val iterator = List(bytesChunk).iterator
    Stream.eval(IO(iterator.next()))
      .unchunks
      .memoize
      .evalMap { stream =>
        stream.compile.drain.as(stream)
      }
      .compile
      .lastOrError
      .flatMap { stream =>
        stream
          .chunkAll
          .compile
          .lastOrError
          .map { testChunk =>
            println("assert")
            assertEquals(testChunk, bytesChunk)
          }
      }
  }

  test("memoize should work on bigger streams") {
    bigStream
      .take(20_000_000)
      .extract(compileHashInfo)
      .flatMap { case (stream, hashInfoIO) =>
        stream
          .memoize
          .evalMap { stream =>
            for {
              testChecksum1Fiber <- compileHashInfo(stream).start
              testChecksum2Fiber <- compileHashInfo(stream).start
              hashInfo <- hashInfoIO
              testChecksum1 <- testChecksum1Fiber.joinWithNever
              testChecksum2 <- testChecksum2Fiber.joinWithNever
            } yield {
              println("assert")
              println(hashInfo)
              assertEquals(testChecksum1, hashInfo)
              assertEquals(testChecksum2, hashInfo)
            }
          }
      }
      .compile
      .drain
  }

  test("stream should not let you more than once".fail) {
    val iterator = List(bytesChunk).iterator
    Stream.emit(
      Stream.eval(IO(iterator.next()))
        .unchunks
    )
      .covary[IO]
      .evalMap { stream =>
        stream.compile.drain.as(stream)
      }
      .compile
      .lastOrError
      .flatMap { stream =>
        stream
          .chunkAll
          .compile
          .lastOrError
          .map { testChunk =>
            println("assert")
            assertEquals(testChunk, bytesChunk)
          }
      }
  }
}
