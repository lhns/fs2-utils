package de.lolhens.fs2.utils

import cats.effect.{IO, unsafe}
import munit.TaglessFinalSuite

import scala.concurrent.Future

import cats.effect.IO
import fs2.{Chunk, Stream}

import scala.util.Random
import Fs2Utils._
import cats.effect.std.Queue
import cats.syntax.parallel._

import scala.concurrent.duration._

abstract class CatsEffectSuite extends TaglessFinalSuite[IO] {
  override protected def toFuture[A](f: IO[A]): Future[A] = f.unsafeToFuture()(unsafe.IORuntime.global)

  private def randomChunk = Chunk.array(Random.nextBytes(1_000_000))

  protected lazy val bytesChunk: Chunk[Byte] = randomChunk

  protected val bigStream: Stream[IO, Byte] = Stream.repeatEval(IO(randomChunk)).unchunks.take(5_000_000_000L)

  protected def zipWithHashInfo(stream: Stream[IO, Byte]): Stream[IO, (Stream[IO, Byte], IO[String])] =
    stream
      .extract(_.size.compile.lastOrError)
      .flatMap { case (stream, sizeIO) =>
        stream
          .extract(_.through(fs2.hash.sha1).through(fs2.text.hex.encode).compile.string)
          .map { case (stream, checksumIO) =>
            (stream, for {
              size <- sizeIO
              checksum <- checksumIO
            } yield s"checksum: $checksum, size: $size")
          }
      }

  protected def compileHashInfo(stream: Stream[IO, Byte]): IO[String] =
    zipWithHashInfo(stream)
      .flatMap { case (stream, hashInfo) =>
        stream.drain ++ Stream.eval(hashInfo)
      }
      .compile
      .lastOrError
}
