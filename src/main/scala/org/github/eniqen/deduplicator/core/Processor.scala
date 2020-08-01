package org.github.eniqen.deduplicator.core

import cats.Parallel
import cats.effect.{Concurrent, Timer}
import org.github.eniqen.deduplicator.domain.DeduplicationResult.{Exist, NotExist}
import org.github.eniqen.deduplicator.domain.Model
import org.github.eniqen.deduplicator.repo.ModelStorage
import cats.syntax.flatMap._

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */

final class ModelProcessor[F[_]: Concurrent: Timer: Parallel] (G: StreamingModelGenerator[F], D: Deduplicator[F, Model], S: ModelStorage[F]) {
  def process: F[Unit] =
    G.generate.through(D.deduplicate).map {
      case Exist(t) =>
        println("Already exist: " + t)
        None
      case NotExist(t) => Some(t)
    }.unNone.through(S.persist(batchSize = 5000)).compile.drain
}

object Processor {
  def start[F[_]: Concurrent: Timer: Parallel](D: Deduplicator[F, Model],S: ModelStorage[F], G: StreamingModelGenerator[F]): F[Unit] =
    Concurrent[F].delay(new ModelProcessor[F](G, D, S)).flatMap(_.process)
}