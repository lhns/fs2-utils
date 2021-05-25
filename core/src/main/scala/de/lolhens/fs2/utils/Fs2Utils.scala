package de.lolhens.fs2.utils

import cats.effect._
import cats.effect.std.{CountDownLatch, Queue}
import cats.effect.syntax.spawn._
import cats.syntax.apply._
import cats.syntax.functor._
import fs2._

object Fs2Utils {
  implicit class StreamUtilsOps[F[_], O](val self: Stream[F, O]) {
    def start(buffer: Int = 1)(implicit ConcurrentF: Concurrent[F]): Stream[F, Stream[F, O]] =
      for {
        queue <- Stream.eval(Queue.bounded[F, Option[Chunk[O]]](buffer))
        fiber <- Stream.eval(self.enqueueNoneTerminatedChunks(queue).compile.drain.start)
      } yield
        Stream.supervise(fiber.joinWithNever) >>
          Stream.fromQueueNoneTerminatedChunk(queue)

    def dupe(buffer: Int = 1)(implicit ConcurrentF: Concurrent[F]): Stream[F, (Stream[F, O], Stream[F, O])] =
      for {
        queue1 <- Stream.eval(Queue.bounded[F, Option[Chunk[O]]](buffer))
        queue2 <- Stream.eval(Queue.bounded[F, Option[Chunk[O]]](buffer))
        c <- Stream.eval(CountDownLatch[F](2))
        _ <- Stream.bracket(
          self.chunks.noneTerminate.foreach[F](e => queue1.offer(e).both(queue2.offer(e)).void).compile.drain.start
        )(fiber => c.await *> fiber.cancel)
      } yield (
        Stream.fromQueueNoneTerminatedChunk(queue1).onFinalize(c.release),
        Stream.fromQueueNoneTerminatedChunk(queue2).onFinalize(c.release)
      )

    def size: Stream[F, Long] = self.fold(0L)((i, _) => i + 1)

    def extract[B](f: Stream[F, O] => F[B], buffer: Int = 1)
                  (implicit ConcurrentF: Concurrent[F]): Stream[F, (Stream[F, O], F[B])] =
      for {
        queue <- Stream.eval(Queue.bounded[F, Option[Chunk[O]]](buffer))
        fiber <- Stream.eval(f(
          self
            .chunks
            .noneTerminate
            .evalTap(queue.offer)
            .unNoneTerminate
            .flatMap(Stream.chunk)
        ).start)
      } yield (
        Stream.fromQueueNoneTerminatedChunk(queue),
        fiber.joinWithNever
      )

    def take(n: Stream[F, Long]): Stream[F, O] = {
      def go(s: Stream[F, O], n: Stream[F, Long]): Pull[F, O, Unit] = {
        n.pull.uncons1.flatMap {
          case Some((n, ntl)) =>
            s.pull.uncons.flatMap {
              case Some((hd, tl)) =>
                hd.size match {
                  case m if m <= n => Pull.output(hd) >> go(tl, ntl.cons(Chunk(n - m).filter(_ > 0)))
                  case _ =>
                    val (hdhd, hdtl) = hd.splitAt(n.toInt)
                    Pull.output(hdhd) >> go(tl.cons(hdtl), ntl)
                }
              case None => Pull.done
            }
          case None => Pull.done
        }
      }

      go(self, n).stream
    }

    def throughIfMoreThan[O2](threshold: Int)(pipe: Pipe[F, O, O2])(otherwise: Pipe[F, O, O2]): Stream[F, O2] = {
      self.pull.unconsN(threshold + 1, allowFewer = true).flatMap(Pull.output1).stream.flatMap {
        case Some((head, tail)) =>
          if (head.size > threshold) pipe(tail.cons(head))
          else otherwise(tail.cons(head))

        case None =>
          Stream.empty
      }
    }
  }
}
