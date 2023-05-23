package de.lolhens.fs2.utils

import cats.effect.IO
import fs2.Chunk

import scala.util.Random
import fs2.Stream
import de.lolhens.fs2.utils.Fs2IoUtils._
import de.lolhens.fs2.utils.Fs2Utils._
import munit._
import fs2.io.file.Files

class Fs2IoUtilsSuite extends CatsEffectSuite {
  private lazy val bytesChunk = Chunk.array(Random.nextBytes(1_000_000))

  test("buffer") {
    val iterator = List(bytesChunk).iterator
    Stream.fromIterator[IO](iterator, 1)
      .unchunks
      .chunkLimit(100)
      .unchunks
      .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1024, maxSizeBeforeWrite = 2048))
      .evalMap { stream =>
        val chunk =
          stream
            .chunkAll
            .compile
            .lastOrError

        chunk.flatMap { testChunk1 =>
          chunk.map { testChunk2 =>
            assertEquals(bytesChunk, testChunk1)
            assertEquals(bytesChunk, testChunk2)
          }
        }
      }
      .compile
      .drain
  }
}
