package de.lolhens.fs2.utils

import cats.effect.IO
import fs2.Chunk

import scala.util.Random
import fs2.Stream
import de.lolhens.fs2.utils.Fs2IoUtils._
import de.lolhens.fs2.utils.Fs2Utils._
import munit._
import fs2.io.file.Files
import scala.concurrent.duration._

class Fs2IoUtilsOomSuite extends CatsEffectSuite {
  override def munitTimeout: Duration = 10.minutes

  test("buffer should not oom") {
    bigStream
      .extract(_.through(fs2.hash.sha1).through(fs2.text.hex.encode).compile.string)
      .flatMap { case (stream, checksumIO) =>
        stream.extract(_.size.compile.lastOrError)
          .map { case (stream, sizeIO) => (stream, checksumIO, sizeIO) }
      }
      .flatMap { case (stream, checksumIO, sizeIO) =>
        println("b")
        stream
          .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1024 * 64, maxSizeBeforeWrite = /*100_000_00*/ 1))
          .evalMap { stream =>
            println("a")
            val calculateChecksum =
              stream.through(fs2.hash.sha1).through(fs2.text.hex.encode).compile.string

            for {
              testChecksum1Fiber <- calculateChecksum.start
              testChecksum2Fiber <- calculateChecksum.start
              checksum <- checksumIO
              size <- sizeIO
              testChecksum1 <- testChecksum1Fiber.joinWithNever
              testChecksum2 <- testChecksum2Fiber.joinWithNever
            } yield {
              println("assert")
              println(size)
              assertEquals(testChecksum1, checksum)
              assertEquals(testChecksum2, checksum)
            }
          }
      }
      .compile
      .drain
  }
}
