package de.lolhens.fs2.utils

import cats.effect.{IO, unsafe}
import munit.TaglessFinalSuite

import scala.concurrent.Future

abstract class CatsEffectSuite extends TaglessFinalSuite[IO] {
  override protected def toFuture[A](f: IO[A]): Future[A] = f.unsafeToFuture()(unsafe.IORuntime.global)
}
