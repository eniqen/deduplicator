package org.github.eniqen.deduplicator

import cats.effect.{Blocker, ConcurrentEffect, ExitCode, IO, IOApp}
import fs2.concurrent.SignallingRef
import org.github.eniqen.deduplicator.core.Generator
import org.github.eniqen.deduplicator.domain.Model
import org.http4s.client.dsl.io._
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl._
import org.http4s.circe._
import org.http4s.client.{Client, JavaNetClientBuilder}
import fs2._
import org.github.eniqen.deduplicator.core.Generator.GenType

import scala.concurrent.ExecutionContextExecutorService

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
object GenApp extends IOApp with Http4sDsl[IO] {
  implicit val modelEncoder: EntityEncoder[IO, Model] = jsonEncoderOf

  import scala.concurrent.ExecutionContext
  import java.util.concurrent._

  val blockingEC: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))
  val uri = uri"http://localhost:8080/data"
  def postRequest(m: Model): IO[Request[IO]] = POST(m, uri)

  override def run(args: List[String]): IO[ExitCode] = start.compile.drain.as(ExitCode.Success)

  def start: Stream[IO, Unit] = for {
    stop  <- Stream.eval(SignallingRef[IO, Boolean](false))
    gen   <- Stream.eval(Generator.stub[IO]("entity-", 1000, GenType.Seq)(stop))
    httpClient: Client[IO] = JavaNetClientBuilder[IO](Blocker.liftExecutionContext(blockingEC)).create
    _     <- gen.generate.parEvalMap(30)(m => httpClient.status(postRequest(m)))
  } yield ()
}

