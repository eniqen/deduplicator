package org.github.eniqen

import java.time.Instant
import java.util.concurrent.TimeUnit
import cats.syntax.functor._
import cats.effect.{Sync, Timer}

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
package object deduplicator {

  def timeNow[F[_]: Sync: Timer]: F[Instant] =
    Timer[F].clock.realTime(TimeUnit.MILLISECONDS).map(Instant.ofEpochMilli)
}
