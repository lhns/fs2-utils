package de.lolhens.fs2.utils

import cats.effect._
import cats.effect.std.Queue
import de.lolhens.fs2.utils.Fs2Utils._
import fs2.{Pipe, Pull, Stream}
import cats.syntax.flatMap._
import cats.syntax.monadError._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.effect.syntax.concurrent._
import fs2.io.file.{Files, Flags, Path, ReadCursor, WriteCursor}
import cats.syntax.traverse._

import scala.concurrent.duration._

object Fs2IoUtils {
  private def readUntilStream[F[_]](cursor: ReadCursor[F], chunkSize: Int, endStream: Stream[F, Long]): Pull[F, Byte, ReadCursor[F]] =
    endStream.pull.uncons1.flatMap {
      case Some((end, endStream)) =>
        cursor.readUntil(chunkSize, end).flatMap { cursor =>
          readUntilStream(cursor, chunkSize, endStream)
        }

      case None =>
        Pull.pure(cursor)
    }

  private def readUntilWait[F[_]](cursor: ReadCursor[F], chunkSize: Int, end: Long, pollDelay: FiniteDuration)
                                 (implicit F: Temporal[F]): Pull[F, Byte, ReadCursor[F]] =
    if (cursor.offset < end) {
      cursor.readUntil(chunkSize, end).flatMap { cursor =>
        Pull.eval(F.sleep(pollDelay)) >>
          readUntilWait(cursor, chunkSize, end, pollDelay)
      }
    } else {
      Pull.pure(cursor)
    }

  implicit class FilesIoUtilsOps[F[_]](val FilesF: Files[F]) extends AnyVal {
    def buffer(fileResource: Resource[F, Path], chunkSize: Int)(implicit F: Async[F], files: Files[F]): Pipe[F, Byte, Stream[F, Byte]] = {
      { stream =>
        for {
          tempFileResource <- Stream.resource(fileResource.memoize)
          writePosSignal <- (for {
            writeCursorResource <- Stream.resource(tempFileResource.flatMap(files.writeCursor(_, Flags.Append)).memoize)
            writePos <- stream
              .chunks
              .zipWithScan1(0L)(_ + _.size)
              .evalMapAccumulate(writeCursorResource) { case (writeCursorResource, (chunk, writePos)) =>
                writeCursorResource
                  .use(_.write(chunk))
                  .map(writeCursor => (Resource.pure[F, WriteCursor[F]](writeCursor), writePos))
              }
              .map(_._2)
          } yield writePos)
            .map((_, true))
            .appendWithLast[F, (Long, Boolean)] {
              case Some((writePos, _)) =>
                Stream.emit((writePos, false))
              case None =>
                Stream.emit((0, false))
            }
            .hold((0L, false))
        } yield for {
          readCursor <- Stream.resource(tempFileResource.flatMap(files.readCursor(_, Flags.Read)))
          e <- readUntilStream(
            readCursor,
            chunkSize,
            writePosSignal
              .discrete
              .takeThrough(_._2)
              .map(_._1)
              .filter(_ > 0L)
          ).flatMap(readCursor =>
            Pull.eval(writePosSignal.get.map(_._1)).flatMap(writePos =>
              readUntilWait(
                readCursor,
                chunkSize,
                writePos,
                100.millis
              ))
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
                .map { case (a, b) => a ++ b }
          }
    }
  }
}
