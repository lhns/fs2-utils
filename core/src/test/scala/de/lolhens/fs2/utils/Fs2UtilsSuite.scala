package de.lolhens.fs2.utils

import cats.effect.{Deferred, IO}
import fs2.{Chunk, Stream}

import scala.util.Random
import Fs2Utils._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class Fs2UtilsSuite extends CatsEffectSuite {
  private lazy val bytesChunk = Chunk.array(Random.nextBytes(1_000_000))

  override def munitTimeout: Duration = 1.second

  test("start: consume more than once should fail".fail) {
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
            assertEquals(bytesChunk, testChunk)
          }
      }
  }

  test("memoize: consume more than once") {
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
            assertEquals(bytesChunk, testChunk)
          }
      }
  }

  test("consume more than once should fail".fail) {
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
            assertEquals(bytesChunk, testChunk)
          }
      }
  }
}
