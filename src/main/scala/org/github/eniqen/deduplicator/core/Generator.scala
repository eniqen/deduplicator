package org.github.eniqen.deduplicator.core

import cats.effect.{Concurrent, Sync}
import fs2.Stream
import fs2.concurrent.{Queue, Signal}
import org.github.eniqen.deduplicator.domain.Model

import scala.io.StdIn
import scala.util.{Random, Try}
import cats.syntax.functor._
import cats.syntax.contravariantSemigroupal._
import org.github.eniqen.deduplicator.core.Generator.GenType

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
trait Generator[F[_], S[_]] {
  def generate: S[Model]
}

trait StreamingModelGenerator[F[_]] extends Generator[F, Stream[F, ?]]

final class StubModelGenerator[F[_]: Concurrent] (prefix: String, max: Int, exitCode: Signal[F, Boolean], genType: GenType) extends StreamingModelGenerator[F] {
  import cats.syntax.functor._
  private val nextValue: F[Long] = Sync[F].delay(Random.nextInt(max).toLong)
  private val nextName: F[String] = nextValue.map(r => s"$prefix$r")

  override def generate: Stream[F, Model] = genType match {
    case GenType.Seq => fromSeq
    case GenType.Random => random
  }

  private def random: Stream[F, Model] = Stream.repeatEval((nextName, nextValue)
    .tupled
    .map((Model.apply _).tupled))
    .interruptWhen(exitCode)

  private def fromSeq: Stream[F, Model] = for {
    i <- Stream.range(1, max + 1)
    j <- Stream.range(1, max + 1)
  } yield Model(s"$prefix$j", i)
}

final class ConsoleGenerator[F[_]: Concurrent](q: Queue[F, String]) extends StreamingModelGenerator[F] {

  def fromString(raw: String): Option[Model] = {
    Try(raw.split(",")).collect {
      case Array(n, v) => Model(n, v.toLong)
    }.toOption
  }

  override def generate: Stream[F, Model] = q.dequeue.map(fromString).unNone concurrently
    Stream.repeatEval(Concurrent[F].delay(StdIn.readLine())).evalMap(q.enqueue1)
}

final class ApiGenerator[F[_]: Concurrent](q: Queue[F, Model]) extends StreamingModelGenerator[F] {

  override def generate: Stream[F, Model] = q.dequeue
}

object Generator {

  sealed trait GenType extends Product with Serializable
  object GenType {
    case object Seq extends GenType
    case object Random extends GenType
  }
  def stub[F[_]: Concurrent](pref: String, max: Int, genType: GenType)(whenStop: Signal[F, Boolean]): F[StreamingModelGenerator[F]] =
    Concurrent[F].delay {
      new StubModelGenerator[F](pref, max, whenStop, genType)
    }

  def console[F[_]: Concurrent](maxSize: Int): F[ConsoleGenerator[F]] =
    Queue.bounded[F, String](maxSize).map(new ConsoleGenerator[F](_))

  def api[F[_]: Concurrent](q: Queue[F, Model]): F[ApiGenerator[F]] = Concurrent[F].delay(new ApiGenerator[F](q))

}
