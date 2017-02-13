package com.github.unisay.moki

import fs2._
import fs2.async._
import fs2.async.mutable.{Queue, Signal}
import fs2.interop.scalaz._
import org.http4s.Uri.RegName
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.Server
import org.http4s.server.blaze._
import fs2.Task

import scalaz.concurrent.{Task => ZTask}

object Moki extends JvmService with ProcessService {

  def httpService(host: String = "localhost", port: Int = 0)(implicit s: Strategy): TestService[MokiClient] =
    TestService(startTask = startHttpServer(host, port).toFs2, stopTask = _.server.shutdown.toFs2)

  private def startHttpServer(host: String, port: Int)(implicit s: Strategy): ZTask[MokiClient] =
    for {
      queue <- boundedQueue[ZTask, Request](maxSize = Int.MaxValue)
      signal <- Signal(HttpService.lift(_ => NotFound()))
      server <- buildServer(queue, signal, host, port)
    } yield new MokiClient(server, queue, signal)

  private def buildServer(queue: Queue[ZTask, Request],
                          signal: Signal[ZTask, HttpService],
                          host: String,
                          port: Int): ZTask[Server] = {
    val service = HttpService {
      case request =>
        for {
          responder <- signal.get
          _         <- queue.enqueue1(request)
          response  <- responder.run(request)
        } yield response
    }
    BlazeBuilder.bindHttp(port, host).mountService(service, "/").start
  }
}

class MokiClient private[moki](val server: Server,
                               private val queue: Queue[ZTask, Request],
                               private val signal: Signal[ZTask, HttpService])
                              (implicit s: Strategy) {
  private val address = server.address
  private val authority = Uri.Authority(host = RegName(address.getHostString), port = Option(address.getPort))
  val uri = Uri(scheme = Some("http".ci), authority = Some(authority))
  def respond(service: HttpService): Task[Unit] = Task((signal set service).unsafePerformSync)
  def respond(f: Request => ZTask[Response]): Task[Unit] = respond(HttpService lift f)
  def received: Task[Int] = queue.available.get.map(Int.MaxValue - _).toFs2
  def requests: Stream[Task, Request] = queue.dequeue.translate(scalazToFs2)
}

