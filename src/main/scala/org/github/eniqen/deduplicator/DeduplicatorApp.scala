package org.github.eniqen.deduplicator

import java.util.concurrent.Executors

import cats.Parallel
import cats.effect.{Concurrent, ConcurrentEffect, ExitCode, IO, IOApp, Timer}
import fs2.concurrent.{Queue, SignallingRef}
import org.github.eniqen.deduplicator.api.EndPoints
import org.github.eniqen.deduplicator.core.{Deduplicator, Generator, Processor}
import org.github.eniqen.deduplicator.domain.Model
import org.github.eniqen.deduplicator.repo.ModelStorage

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */
object DeduplicatorApp extends IOApp {
  import fs2._
  import Model._

  override def run(args: List[String]): IO[ExitCode] = start[IO].compile.drain.as(ExitCode.Success)
  val r = Runtime.getRuntime
  val blockingEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(20))
  val windowsSize = 5.hours

  def start[F[_]: ConcurrentEffect: Timer: Parallel]: Stream[F, Unit] = for {
    storage       <- Stream.eval(ModelStorage.mutable)
    _             <- Stream.eval(Concurrent[F].delay(println("Memory usage: "+ (r.totalMemory - r.freeMemory) + " of " + r.maxMemory)))
    stop          <- Stream.eval(SignallingRef(false))
//    generator   <- Stream.eval(Generator.console(10))
//    partitioner <- Stream.emit(Partitioner.init[F]())
    source        <- Stream.eval(Queue.unbounded[F, Model])
    generator     <- Stream.eval(Generator.api(source))
    deduplicator  <- Stream.eval(Deduplicator.apply[F, Model](windowsSize))
    modelApi      <- Stream.eval(EndPoints.modelApi(storage))
    _             <- Stream.resource(EndPoints.run(modelApi.endpoints(source))(blockingEC)).flatMap(_ => Stream.never) concurrently
                     Stream.eval(Processor.start(deduplicator, storage, generator))
  } yield ()
}
