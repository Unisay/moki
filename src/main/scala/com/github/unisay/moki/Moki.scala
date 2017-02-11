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

import scalaz.concurrent.Task

object Moki extends JvmService with ProcessService {

  def httpService(host: String = "localhost", port: Int = 0): TestService[MokiClient] =
    TestService(startHttpServer(host, port), (_: MokiClient).server.shutdown)

  private def startHttpServer(host: String, port: Int): Task[MokiClient] =
    for {
      queue  <- boundedQueue[Task, Request](maxSize = Int.MaxValue)
      signal <- Signal(HttpService.lift(_ => NotFound()))
      server <- buildServer(queue, signal, host, port)
    } yield new MokiClient(server, queue, signal)

  private def buildServer(queue: Queue[Task, Request],
                          signal: Signal[Task, HttpService],
                          host: String,
                          port: Int): Task[Server] = {
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
                               private val queue: Queue[Task, Request],
                               private val signal: Signal[Task, HttpService]) {
  private val address = server.address
  private val authority = Uri.Authority(host = RegName(address.getHostString), port = Option(address.getPort))
  val uri = Uri(scheme = Some("http".ci), authority = Some(authority))
  def respond(service: HttpService): Task[Unit] = Task.fork(signal set service)
  def respond(f: Request => Task[Response]): Task[Unit] = respond(HttpService lift f)
  def received: Task[Int] = Task.fork(queue.available.get.map(Int.MaxValue - _))
  def requests: Stream[Task, Request] = queue.dequeue
}

