package de.lolhens.fs2.utils

import cats.effect._
import cats.effect.std.Queue
import de.lolhens.fs2.utils.Fs2Utils._
import fs2._
import fs2.io.file.{Files, Flags, Path}

object Fs2IoUtils {
  implicit class FilesIoUtilsOps[F[_]](val FilesF: Files[F]) extends AnyVal {
    def create(path: java.nio.file.Path)(implicit SyncF: Sync[F]): F[Unit] =
      SyncF.blocking(java.nio.file.Files.createFile(path))

    def create(path: Path)(implicit SyncF: Sync[F]): F[Unit] =
      create(path.toNioPath)

    def buffer(path: java.nio.file.Path, chunkSize: Int)(implicit AsyncF: Async[F]): Pipe[F, Byte, Byte] =
      buffer(Path.fromNioPath(path), chunkSize)

    def buffer(path: Path, chunkSize: Int = 64 * 1024)(implicit AsyncF: Async[F]): Pipe[F, Byte, Byte] = { stream =>
      for {
        chunkSizes <- Stream.eval(Queue.unbounded[F, Option[Int]])
        _ <- stream
          .chunks
          .noneTerminate
          .evalTap(chunk => chunkSizes.offer(chunk.map(_.size)))
          .unNoneTerminate
          .flatMap(Stream.chunk)
          .through(FilesF.writeAll(path, flags = Flags.Write))
          .spawn
        e <- FilesF.tail(path, chunkSize).take(Stream.fromQueueNoneTerminated(chunkSizes).map(_.toLong))
      } yield e
    }

    def bufferTempFile(chunkSize: Int)(implicit AsyncF: Async[F]): Pipe[F, Byte, Byte] = { stream =>
      Stream.resource(FilesF.tempFile).flatMap { path =>
        stream.through(FilesF.buffer(path, chunkSize))
      }
    }
  }
}
