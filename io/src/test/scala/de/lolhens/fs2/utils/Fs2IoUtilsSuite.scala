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
  test("buffer") {
    for {
      expectedHashInfo <- compileHashInfo(Stream.chunk(bytesChunk))
      iterator = List(bytesChunk).iterator
      _ <- Stream.fromIterator[IO](iterator, 1)
        .unchunks
        .chunkLimit(1_000)
        .unchunks
        .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1_000))
        .evalMap { stream =>
          for {
            hashInfo1 <- compileHashInfo(stream)
            hashInfo2 <- compileHashInfo(stream)
          } yield {
            println("assert")
            assertEquals(hashInfo1, expectedHashInfo)
            assertEquals(hashInfo2, expectedHashInfo)
          }
        }
        .compile
        .drain
    } yield ()
  }

  test("buffer maxSizeBeforeWrite") {
    for {
      expectedHashInfo <- compileHashInfo(Stream.chunk(bytesChunk))
      iterator = List(bytesChunk).iterator
      _ <- Stream.fromIterator[IO](iterator, 1)
        .unchunks
        .chunkLimit(100)
        .unchunks
        .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1024 , maxSizeBeforeWrite = 2048))
        .evalMap { stream =>
          for {
            hashInfo1 <- compileHashInfo(stream)
            hashInfo2 <- compileHashInfo(stream)
          } yield {
            println("assert")
            assertEquals(hashInfo1, expectedHashInfo)
            assertEquals(hashInfo2, expectedHashInfo)
          }
        }
        .compile
        .drain
    } yield ()
  }

  test("buffer maxSizeBeforeWrite=0") {
    for {
      expectedHashInfo <- compileHashInfo(Stream.chunk(bytesChunk))
      iterator = List(bytesChunk).iterator
      _ <- Stream.fromIterator[IO](iterator, 1)
        .unchunks
        .chunkLimit(100)
        .unchunks
        .through(Files[IO].buffer(Files[IO].tempFile, chunkSize = 1024, maxSizeBeforeWrite = 0))
        .evalMap(compileHashInfo)
        .compile
        .lastOrError
        .map { hashInfo =>
          println("assert")
          assertEquals(hashInfo, expectedHashInfo)
        }
    } yield ()
  }
}
