package moki

import fs2._
import fs2.async._
import fs2.async.mutable.{Queue, Signal}
import fs2.interop.scalaz._
import org.http4s.{HttpService, _}
import org.http4s.server.Server
import org.http4s.server.blaze._

import scalaz.concurrent.Task

object Moki {

  def httpService(host: String = "localhost", port: Int): TestService[MokiClient, MokiClient] =
    TestService(startServer(host, port), _.shutdownServer)

  def startServer(host: String, port: Int): Task[MokiClient] =
    for {
      queue  <- boundedQueue[Task, Request](maxSize = Int.MaxValue)
      signal <- Signal((_: Request) => Response(Status.NotFound))
      server <- buildServer(queue, signal, host, port)
    } yield new MokiClient(queue, signal, server)

  private def buildServer(queue: Queue[Task, Request],
                          signal: Signal[Task, Request => Response],
                          host: String, port: Int): Task[Server] = {
    val service = HttpService {
      case request =>
        for {
          responder <- signal.get
          _ <- queue.enqueue1(request)
        } yield responder(request)
    }
    BlazeBuilder
      .bindHttp(port, host)
      .mountService(service, "/")
      .start
  }
}

class MokiClient private[moki](private val queue: Queue[Task, Request],
                               private val signal: Signal[Task, Request => Response],
                               private val server: Server) {
  def setResponder(f: Request => Response): Task[Unit] = signal set f
  def received: Task[Int] = queue.available.get.map(Int.MaxValue - _)
  def requests: Stream[Task, Request] = queue.dequeue
  def shutdownServer: Task[Unit] = server.shutdown
}
