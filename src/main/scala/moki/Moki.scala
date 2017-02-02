package moki

import fs2._
import fs2.async._
import fs2.async.mutable.Queue
import fs2.interop.scalaz._
import org.http4s._
import org.http4s.server.Server
import org.http4s.server.blaze._

import scalaz.concurrent.Task
import scalaz.syntax.functor._

object Moki {
  def startServer(host: String, port: Int): Task[MokiClient] =
    for {
      queue  <- unboundedQueue[Task, Request]
      server <- server(queue, host, port)
    } yield new MokiClient(queue.dequeue, server)

  private def server(queue: Queue[Task, Request], host: String, port: Int): Task[Server] = {
    val httpService = HttpService {
      case request =>
        queue.enqueue1(request).as(Response(Status.Ok)) // TODO: consider offer?
    }
    BlazeBuilder
      .bindHttp(port, host)
      .mountService(httpService, "/")
      .start
  }
}

class MokiClient private[moki](val requests: Stream[Task, Request], private val server: Server) {
  def shutdownServer: Task[Unit] = server.shutdown
}
