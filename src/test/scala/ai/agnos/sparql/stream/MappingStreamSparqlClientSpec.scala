package ai.agnos.sparql.stream

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.testkit.scaladsl.{TestSink, TestSource}
import org.apache.pekko.testkit.TestKit
import ai.agnos.sparql.SparqlQueries
import ai.agnos.sparql.api._
import ai.agnos.sparql.stream.client.{HttpClientFlowBuilder, HttpEndpointFlow, SparqlRequestFlowBuilder}
import ai.agnos.test.HttpEndpointSuiteTestRunner
import org.scalatest._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

/**
  * This test runs as part of the [[HttpEndpointSuiteTestRunner]] Suite.
  */
@DoNotDiscover
class MappingStreamSparqlClientSpec() extends TestKit(ActorSystem("MappingStreamSparqlClientSpec"))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with SparqlQueries with SparqlRequestFlowBuilder with HttpClientFlowBuilder {

  implicit val materializer: Materializer = Materializer(system)
  implicit val dispatcher: ExecutionContext = system.dispatcher
  implicit val prefixMapping: PrefixMapping = PrefixMapping.none

  implicit val errorHandler: ErrorHandler = DefaultErrorHandler

  val receiveTimeout: FiniteDuration = 5 seconds

  import HttpEndpointSuiteTestRunner._

  "The Akka-Streams Sparql Client" must {
    val sparqlRequestFlowUnderTest = sparqlRequestFlow(HttpEndpointFlow(testServerEndpoint, defaultHttpClientFlow[SparqlRequest]))

    val ( source, sink ) = TestSource.probe[SparqlRequest]
      .via(sparqlRequestFlowUnderTest)
      .toMat(TestSink.probe[SparqlResponse])(Keep.both)
      .run()

    "1. Clear the data" in {
      sink.request(1)
      source.sendNext(SparqlRequest(dropGraph))

      assertSuccessResponse(sink.expectNext(receiveTimeout))

      sink.request(1)
      source.sendNext(SparqlRequest(query1))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result === emptyResult)
        case _ => fail("unexpected response")
      }
    }

    "2. Allow one insert" in {
      sink.request(1)
      source.sendNext(SparqlRequest(insert1))

      assertSuccessResponse(sink.expectNext(receiveTimeout))
    }

    "3. Get the MAPPED results just inserted via HTTP GET" in {
      sink.request(1)
      source.sendNext(SparqlRequest(mappingQuery2Get))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result === mappedQuery1Result)
        case r@SparqlResponse(_, _, _, _, _) => fail(s"unexpected: $r")
      }
    }

    "4. Get the MAPPED results just inserted via HTTP POST" in {
      sink.request(1)
      source.sendNext(SparqlRequest(mappingQuery2Post))

      sink.expectNext(receiveTimeout) match {
        case SparqlResponse (_, true, _, result, None) => assert(result === mappedQuery1Result)
        case r@SparqlResponse(_, _, _, _, _) => fail(s"unexpected: $r")
      }
    }

    "5. Stream must complete gracefully" in {
      source.sendComplete()
      sink.expectComplete()
    }

  }

  private def assertSuccessResponse(response: SparqlResponse): Unit = response match {
    case SparqlResponse(_, true, _, _, _) => assert(true)
    case x@SparqlResponse(_, _, _, _, _) => fail(s"unexpected: $x")
  }

  override def afterAll(): Unit = {
    Await.result(Http().shutdownAllConnectionPools(), 5 seconds)
    Await.result(system.terminate(), 5 seconds)
  }

}
