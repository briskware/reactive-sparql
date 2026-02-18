package ai.agnos.sparql.stream.client

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.apache.pekko.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import org.apache.pekko.stream.{Materializer, OverflowStrategy, QueueOfferResult }
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink, Source}
import ai.agnos.sparql.util.HttpEndpoint

import javax.net.ssl.SSLContext
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}


trait HttpClientFlowBuilder {

  def defaultHttpClientFlow[T](endpoint: HttpEndpoint)
                              (implicit system: ActorSystem, materializer: Materializer)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    queuedAndPooledHttpClientFlow[T](endpoint, queueSize = 10, overflowStrategy = OverflowStrategy.backpressure)
  }

  /**
    * Returns the default pooled HTTP client flow
    * @param endpoint the endpoint to connect to
    * @param httpsContext optional HTTPS connection context
    * @tparam T the type parameter
    * @return the flow
    */
  def pooledHttpClientFlow[T](endpoint: HttpEndpoint, httpsContext: Option[HttpsConnectionContext] = None)
                             (implicit system: ActorSystem)
  : Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {
    endpoint.protocol match {
      case "http" => Flow[(HttpRequest, T)].via(Http().cachedHostConnectionPool(endpoint.host, endpoint.port))
      case "https" =>
        val ctx = httpsContext.getOrElse(ConnectionContext.httpsClient(SSLContext.getDefault))
        Flow[(HttpRequest, T)].via(Http().cachedHostConnectionPoolHttps(endpoint.host, endpoint.port, ctx))
      case protocol => throw new IllegalArgumentException(s"invalid protocol specified: ${protocol}")
    }
  }

  /**
    * Creates a flow that allows for access to the connection pool via a bounded request queue.
    *
    * {@see https://doc.akka.io/docs/akka-http/current/client-side/host-level.html#using-the-host-level-api-with-a-queue}
    *
    * @param endpoint the HTTP(S) endpoint
    * @param queueSize the size of the queue
    * @param overflowStrategy the overflow strategy to apply if the queue overruns
    * @param httpsContext optional HTTPS connection context
    * @param system the actor system
    * @param materializer the materializer
    * @tparam T the type parameter
    * @return the flow
    */
  def queuedAndPooledHttpClientFlow[T]
  (
    endpoint: HttpEndpoint,
    queueSize: Int = 10,
    overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure,
    httpsContext: Option[HttpsConnectionContext] = None
  )
  (
    implicit system: ActorSystem, materializer: Materializer
  ): Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] = {

    implicit val _ec = system.dispatcher

    // Materialize a queue with the desired buffer and overflow strategy
    val queue = Source.queue[(HttpRequest, Promise[HttpResponse])](queueSize, overflowStrategy)
      .via(pooledHttpClientFlow[Promise[HttpResponse]](endpoint, httpsContext))
      .toMat(Sink.foreach({
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p)) => p.failure(e)
      }))(Keep.left)
      .run()

    // queue a request onto the buffered connection pool stream
    def queueRequest(request: HttpRequest): Future[HttpResponse] = {
      val responsePromise = Promise[HttpResponse]()
      queue.offer(request -> responsePromise).flatMap {
        case QueueOfferResult.Enqueued    => responsePromise.future
        case QueueOfferResult.Dropped     => Future.failed(new RuntimeException("HTTP connection pool Queue has overflown. Try again later."))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
        case QueueOfferResult.QueueClosed => Future.failed(new RuntimeException("HTTP connection pool wwas closed (pool shut down) while running the request. Try again later."))
      }
    }

    // Make a flow that routes requests along the connection pool and maintains all possible error scenarios, including
    // 1) HTTP request failures
    // 2) queue overflowStrategy
    val flow: Flow[(HttpRequest, T), (Try[HttpResponse], T), NotUsed] =
      Flow[(HttpRequest, T)]
        .mapAsync(1) {
          case (req, ctx) =>
            // a trick to maintain the inner future's value, so it can be unwrapped via mapAsync above
            // which will give a meaningful error about what has failed to the user
            queueRequest(req).transform {
              case s@Success(_) => Success((s, ctx))
              case f@Failure(_) => Success((f, ctx))
            }
        }
    flow
  }
}
