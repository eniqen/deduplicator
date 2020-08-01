package org.github.eniqen.deduplicator.api

import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import fs2.concurrent.Queue
import org.github.eniqen.deduplicator.repo.ModelStorage
import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes}
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import cats.implicits._
import org.github.eniqen.deduplicator.domain.Model
import org.http4s.server.{Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s._
import org.http4s.implicits._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext


/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
class ModelEndpoint[F[_]: Sync](S: ModelStorage[F]) extends Http4sDsl[F] {
  implicit val modelDecoder: EntityDecoder[F, Model] = jsonOf
  implicit val modelEncoder: EntityEncoder[F, Model] = jsonEncoderOf
  implicit val modelListEncoder: EntityEncoder[F,   List[Model]] = jsonEncoderOf

  def push(q: Queue[F, Model]): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root =>
      req.decode[Model](m => q.enqueue1(m).flatMap(_ => Ok()))
  }

  def pull: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root => S.counts.flatMap(Ok(_))
  }

  def endpoints(q: Queue[F, Model]): HttpApp[F] = Router("/data" -> push(q), "/reports" -> pull).orNotFound
}

object EndPoints {

  def modelApi[F[_]: Sync](S: ModelStorage[F]): F[ModelEndpoint[F]] = Sync[F].delay {
    new ModelEndpoint[F](S)
  }

  def run[F[_]: ConcurrentEffect: Timer](endpoints: HttpApp[F])(executionContext: ExecutionContext): Resource[F, Server[F]] =
    BlazeServerBuilder.apply[F](executionContext)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(endpoints)
      .withIdleTimeout(15.minutes)
      .withResponseHeaderTimeout(15.minutes)
      .resource
}
