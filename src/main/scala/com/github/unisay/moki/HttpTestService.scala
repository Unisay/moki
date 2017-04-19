package com.github.unisay.moki

import fs2._
import fs2.async._
import fs2.async.mutable.{Queue, Signal}
import org.http4s.Uri.RegName
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.Server
import org.http4s.server.blaze._
import fs2.Task
import fs2.util.Async

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait HttpTestService {

  def httpService(host: String = "localhost", port: Int = 0)(implicit s: Strategy): TestService[MokiClient] =
    TestService(
      start = startHttpServer(host, port),
      stop = _.server.shutdown)

  private def startHttpServer(host: String, port: Int)(implicit s: Strategy): Task[MokiClient] =
    for {
      queue <- boundedQueue[Task, Request](maxSize = Int.MaxValue)
      signal <- Signal(HttpService { case _ => NotFound() })(Async[Task])
      server <- buildServer(queue, signal, host, port)
    } yield new MokiClient(server, queue, signal)

  private def buildServer(queue: Queue[Task, Request],
                          signal: Signal[Task, HttpService],
                          host: String,
                          port: Int): Task[Server] = {
    val service = HttpService {
      case request =>
        for {
          _         <- queue.enqueue1(request)
          responder <- signal.get
          response  <- responder.run(request)
        } yield response.orNotFound
    }
    BlazeBuilder.bindHttp(port, host).mountService(service, "/").start
  }
}

class MokiClient private[moki](val server: Server,
                               private val queue: Queue[Task, Request],
                               private val signal: Signal[Task, HttpService])
                              (implicit s: Strategy) {
  private val address = server.address
  private val authority = Uri.Authority(host = RegName(address.getHostString), port = Option(address.getPort))
  val uri = Uri(scheme = Some("http".ci), authority = Some(authority))
  def respond(service: HttpService): Task[Unit] = signal.set(service).async(s)
  def respond(f: Request => Task[Response]): Task[Unit] = respond(HttpService lift f)
  def received: Task[Int] = queue.available.get.map(Int.MaxValue - _)
  def requests: Stream[Task, Request] = queue.dequeue
}

object HttpTestService extends HttpTestService
