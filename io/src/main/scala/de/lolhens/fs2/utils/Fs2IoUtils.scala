package de.lolhens.fs2.utils

import cats.effect._
import cats.effect.std.Queue
import de.lolhens.fs2.utils.Fs2Utils._
import fs2.{Stream, Pipe, Pull}
import cats.syntax.flatMap._
import cats.syntax.monadError._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.effect.syntax.concurrent._
import fs2.io.file.{Files, Flags, Path, ReadCursor}
import cats.syntax.traverse._

object Fs2IoUtils {
  implicit class FilesIoUtilsOps[F[_]](val FilesF: Files[F]) extends AnyVal {
    def buffer(fileResource: Resource[F, Path], chunkSize: Int)(implicit F: Async[F], files: Files[F]): Pipe[F, Byte, Stream[F, Byte]] = {
      def readUntil(cursor: ReadCursor[F], readPosStream: Stream[F, Option[Long]]): Pull[F, Byte, ReadCursor[F]] =
        readPosStream.pull.uncons1.flatMap {
          case Some((readPosOption, readPosStream)) =>
            readPosOption.fold[Pull[F, Byte, ReadCursor[F]]](
              cursor.readAll(chunkSize)
            )(readPos =>
              cursor.readUntil(chunkSize, readPos)
            ).flatMap { cursor =>
              readUntil(cursor, readPosStream)
            }

          case None =>
            Pull.pure(cursor)
        }

      { stream =>
        for {
          tempFileResource <- Stream.resource(fileResource.memoize)
          writePosSignal <- (for {
            stream <- stream.allocated
            tempFile <- Stream.resource(tempFileResource)
            writeCursor <- Stream.resource(files.writeCursor(tempFile, Flags.Append))
            writePos <- stream
              .chunks
              .zipWithScan1(0L)(_ + _.size)
              .evalMapAccumulate(writeCursor) { case (writeCursor, (chunk, writePos)) =>
                writeCursor.write(chunk)
                  .map((_, writePos))
              }
              .map(_._2)
          } yield writePos)
            .noneTerminate
            .hold(Some(0L))
        } yield for {
          tempFile <- Stream.resource(tempFileResource)
          readCursor <- Stream.resource(files.readCursor(tempFile, Flags.Read))
          e <- readUntil(
            readCursor,
            writePosSignal
              .discrete
              .takeThrough(_.isDefined)
              .filter(_.forall(_ > 0))
          ).void.stream
        } yield e
      }
    }

    def buffer(fileResource: Resource[F, Path], chunkSize: Int, maxSizeBeforeWrite: Int)
              (implicit F: Async[F], files: Files[F]): Pipe[F, Byte, Stream[F, Byte]] = {
      stream =>
        stream
          .splitAt(maxSizeBeforeWrite)
          .flatMap {
            case (a, b) =>
              a.memoize
                .zip(b.through(buffer(fileResource, chunkSize)))
                .map{case (a, b) => a ++ b}
          }
    }
  }
}
