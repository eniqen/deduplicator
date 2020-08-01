package org.github.eniqen.deduplicator.repo

import java.util.concurrent.ConcurrentHashMap

import cats.{Functor, Parallel}
import cats.data.Chain
import cats.effect.{Concurrent, Sync, Timer}
import cats.effect.concurrent.Ref
import cats.syntax.functor._
import fs2.Pipe
import org.github.eniqen.deduplicator.domain.Model

import scala.collection.mutable
import cats.syntax.parallel._

import scala.concurrent.duration._


/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
trait ModelStorage[F[_]] {
  def update(m: Model): F[Unit]
  def getAll: F[List[Model]]
  def countBy(name: String): F[Long]
  def getBy(name: String): F[Option[(String, Chain[Long])]]
  def counts: F[List[Model]]
}

object ModelStorage {
  def inMemory[F[_]: Sync]: F[ModelStorage[F]] = Ref.of(Map.empty[String, Chain[Long]]).map(new InMemmoryModelStorage[F](_))

  def mutable[F[_]: Sync]: F[ModelStorage[F]] = Sync[F].delay {
    new MutableModelStorage[F](new ConcurrentHashMap[String, Chain[Long]]())
  }


  final implicit class ModelStorageOps[F[_]](private val S: ModelStorage[F]) extends AnyVal {
    def persist(batchSize: Int)(implicit P: Parallel[F], C: Concurrent[F], T: Timer[F]): Pipe[F, Model, Unit] =
      _.groupWithin(batchSize, 1.second)
        .map(ch => { println(s"To Persist: ${ch.toList.mkString(",")}"); ch})
        .evalMap(ch => ch.parTraverse_(S.update))
  }
}

final class InMemmoryModelStorage[F[_]: Functor] (ref: Ref[F, Map[String, Chain[Long]]]) extends ModelStorage[F] {

  override def update(m: Model): F[Unit] =
    ref.update(s => s.updated(m.name, s.getOrElse(m.name, Chain.empty).append(m.value)))

  override def getAll: F[List[Model]] = ref.get.map {
    _.flatMap {
      case (k, v) => v.map(Model(k, _)).toList
    }(collection.breakOut)
  }

  override def countBy(name: String): F[Long] =
    getBy(name).map(_.fold(0L)(_._2.size))

  override def counts: F[List[Model]] =
    ref.get.map(s => s.map { case (k, v) => Model(k, v.size) }(collection.breakOut))

  override def getBy(name: String): F[Option[(String, Chain[Long])]] = ref.get.map(m => m.get(name).map(name -> _))
}

class MutableModelStorage[F[_]: Sync](private val inner: ConcurrentHashMap[String, Chain[Long]]) extends ModelStorage[F] {
  override def update(m: Model): F[Unit] = Sync[F].delay {
    inner.compute(m.name, (_, fromStore) => {
      Option(fromStore).fold(Chain.empty[Long])(identity).append(m.value)
    })
  }.void

  override def getAll: F[List[Model]] = Sync[F].delay {
    val builder = mutable.Buffer.newBuilder[Model]
    inner.forEach((t: String, u: Chain[Long]) => u.foldLeft(builder) {
      case (acc, v) => acc += Model(t, v)
    })
    builder.result.toList
  }

  override def getBy(name: String): F[Option[(String, Chain[Long])]] = Sync[F].delay {
    Option(inner.get(name)).map(name -> _)
  }

  override def counts: F[List[Model]] = Sync[F].delay {
    val builder = mutable.Buffer.newBuilder[Model]
    inner.forEach((t: String, u: Chain[Long]) => builder += Model(t, u.size))
    builder.result().toList
  }

  override def countBy(name: String): F[Long] = Sync[F].delay {
    Option(inner.get(name)).fold(0L)(_.size)
  }
}