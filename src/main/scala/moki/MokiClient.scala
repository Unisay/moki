package moki

import fs2.{Strategy, Stream}
import fs2.async._
import fs2.async.mutable.Queue
import org.http4s._
import org.http4s.dsl._
import org.http4s.server.Server
import org.http4s.server.blaze._

import scalaz.Scalaz._
import scalaz.concurrent.Task

class MokiClient {
  implicit val s: Strategy = Strategy.fromFixedDaemonPool(maxThreads = 1, threadName = "Moki strategy")

  private val httpService = HttpService {
    case request =>
      Ok("")
  }

  def start(): Stream[Task, Request] = {
    BlazeBuilder.bindHttp(port = 8080, host = "localhost").mountService(httpService, "/").start |@|
      unboundedQueue[Task, Request] map { (server: Server, queue: Queue[Task, Request]) =>

    }
  }

}
