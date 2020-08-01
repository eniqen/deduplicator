package org.github.eniqen.deduplicator.core

import java.time.Instant

import bloomfilter.CanGenerateHashFrom
import bloomfilter.mutable.BloomFilter
import cats.effect.{Concurrent, Sync, Timer}
import org.github.eniqen.deduplicator.domain.DeduplicationResult

import scala.concurrent.duration.FiniteDuration
import fs2.{Pipe, Stream}
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.functor._

import scala.concurrent.duration._
/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
trait Deduplicator[F[_], T] {
  def deduplicate: Pipe[F, T, DeduplicationResult[T]]
}

final class BloomDeduplicator[F[_]: Concurrent: Timer, T: CanGenerateHashFrom](
       window: FiniteDuration,
       expect: Long,
       positiveRate: Double
) extends Deduplicator[F, T] {

  private def check(m: T)(bloomFilter: BloomFilter[T]): DeduplicationResult[T] = if (bloomFilter.mightContain(m)) {
    DeduplicationResult.exist(m)
  } else {
    bloomFilter.add(m)
    DeduplicationResult.notExist(m)
  }

  private def newFilter: (BloomFilter[T], Instant) = BloomFilter[T](expect, positiveRate) -> Instant.now

  override def deduplicate: Pipe[F, T, DeduplicationResult[T]] =
    _.groupWithin(5000, 1.second).evalMapAccumulate(newFilter) {
    case ((filter, createdAt), chunk) if Instant.now isAfter createdAt.plusMillis(window.toMillis) =>
      Sync[F].guarantee(Sync[F].delay(newFilter).map {
        case acc @ (f, _) => acc -> chunk.map(check(_)(f))
      })(Sync[F].delay(filter.dispose()).handleErrorWith(_ => ().pure[F]))
    case (acc @(f, _), chunk) =>
      Sync[F].delay(acc -> chunk.map(check(_)(f)))
  }.flatMap { case (_, ch) => Stream.chunk(ch) }
}

object Deduplicator {
  def apply[F[_]: Concurrent: Timer, T: CanGenerateHashFrom](window: FiniteDuration = 1.minutes ,expect: Long = 10000000000L, posRate: Double = 0.1): F[Deduplicator[F, T]] =
    Sync[F].delay {
      new BloomDeduplicator[F, T](window, expect, posRate)
  }
}