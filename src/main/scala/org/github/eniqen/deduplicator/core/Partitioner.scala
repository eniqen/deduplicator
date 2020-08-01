package org.github.eniqen.deduplicator.core

import java.nio.charset.StandardCharsets

import cats.effect.Concurrent
import fs2.concurrent.Queue
import org.github.eniqen.deduplicator.domain.Model
import cats.implicits._
import fs2._

import scala.collection.mutable.ArrayBuffer
import scala.util.hashing.MurmurHash3

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
case class Partitioner[F[_]: Concurrent](source: Queue[F, Model])(val shards: Array[Queue[F, Model]]) {

  def getShardIdFor(key: String, max: Int): Int = MurmurHash3.bytesHash(key.getBytes(StandardCharsets.UTF_8.name)) % max

  def run: Stream[F, Unit] =
    source.dequeue
      .groupAdjacentBy(m => getShardIdFor(m.name, shards.length))
      .parEvalMap(shards.length) {
        case (shardId, chunk) =>
          Stream.chunk(chunk).through(shards(shardId).enqueue).compile.drain
      }
}

object Partitioner {
  def init[F[_]: Concurrent](source: Queue[F, Model], shards: Int = 31): F[Partitioner[F]] =
    Stream.eval(Queue.unbounded[F, Model])
      .repeatN(shards)
      .fold(ArrayBuffer.empty[Queue[F, Model]])(_ += _)
      .compile
      .lastOrError
      .map(buf => new Partitioner[F](source)(buf.toArray))
      .flatTap(_.run.compile.drain)
}