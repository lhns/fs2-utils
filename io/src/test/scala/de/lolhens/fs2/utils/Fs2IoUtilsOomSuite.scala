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
      .extract(compileHashInfo)
      .flatMap { case (stream, hashInfoIO) =>
        println("b")
        stream
          .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1024 * 64, maxSizeBeforeWrite = 100_000_00))
          .evalMap { stream =>
            println("a")
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
}
