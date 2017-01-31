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

//  implicit val s: Strategy = Strategy.fromFixedDaemonPool(maxThreads = 10, threadName = "Moki strategy")

object Moki {
  def startServer: Task[MokiClient] =
    for {
      queue  <- unboundedQueue[Task, Request]
      server <- server(queue)
    } yield new MokiClient(queue.dequeue, server)

  private def server(queue: Queue[Task, Request]): Task[Server] = {
    val httpService = HttpService {
      case request =>
        queue.enqueue1(request).as(Response(Status.Ok)) // TODO: consider offer?
    }
    BlazeBuilder
      .bindHttp(port = 8080, host = "localhost")
      .mountService(httpService, "/")
      .start
  }
}

class MokiClient private[moki](val requests: Stream[Task, Request], private val server: Server) {
  def shutdownServer: Task[Unit] = server.shutdown
}
