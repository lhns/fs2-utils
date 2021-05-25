package de.lolhens.fs2.utils

import cats.effect.Concurrent
import cats.syntax.flatMap._
import fs2.Stream
import fs2.concurrent.{Broadcast, Queue}

object Fs2Utils {
  implicit class StreamOps[F[_], O](val self: Stream[F, O]) {
    def dupe(implicit ConcurrentF: Concurrent[F]): Stream[F, (Stream[F, O], Stream[F, O])] =
      self
        .through(Broadcast(2))
        .take(2)
        .chunkN(2)
        .map(_.toList match {
          case List(a, b) => (a, b)
        })

    def size: Stream[F, Long] = self.fold(0L)((i, _) => i + 1)

    def extract[B](f: Stream[F, O] => F[B])
                  (implicit ConcurrentF: Concurrent[F]): Stream[F, (Stream[F, O], F[B])] =
      Stream.eval(Queue.bounded[F, B](1)).flatMap { queue =>
        self.dupe.flatMap {
          case (a, b) =>
            Stream(
              Stream((a, queue.dequeue1)),
              Stream.eval_(f(b).flatMap(queue.enqueue1))
            ).parJoinUnbounded
        }
      }
  }
}
