package de.is24.jest4s

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ ActorMaterializer, Materializer }
import io.searchbox.client.config.HttpClientConfig
import io.searchbox.client.{ JestClient, JestClientFactory }
import java.net.URI
import org.specs2.specification.Scope
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import play.api.Configuration
import play.api.test.{ DefaultAwaitTimeout, FutureAwaits }
import scala.collection.generic.CanBuildFrom
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import language.higherKinds

trait StatefulElasticSpec extends Specification with EmbeddedElasticSearchSupport with BeforeAfterAll with FutureAwaits with DefaultAwaitTimeout {
  val specsActorSystem = ActorSystem("testSystem")
  override implicit def executionContext: ExecutionContext = specsActorSystem.dispatcher
  val specsMaterializer: Materializer = ActorMaterializer()(specsActorSystem)

  def beforeAll: Unit = {
    startEmbeddedElasticSearch()
  }

  def afterAll: Unit =
    await(Future.sequence(Seq(
      stopEmbeddedElasticSearch(),
      specsActorSystem.terminate()
    )))

  def collectingSink[U, T[_]](implicit cbf: CanBuildFrom[T[U], U, T[U]]): Sink[U, Future[T[U]]] =
    Sink.fold(cbf()) { (builder, element: U) ⇒
      builder += element
    }.mapMaterializedValue(_.map(_.result()))

  trait WithElasticClient extends Scope {
    implicit val actorSystem: ActorSystem = specsActorSystem
    implicit val materializer: Materializer = specsMaterializer
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    lazy val elasticSearchHttpUri: ElasticSearchHttpUri = ElasticSearchHttpUri(new URI("http://localhost:9200"))

    private lazy val jestClient: JestClient = {
      val factory = new JestClientFactory()
      factory.setHttpClientConfig(new HttpClientConfig.Builder(elasticSearchHttpUri.uri.toString)
        .multiThreaded(true)
        .build())
      factory.getObject
    }
    lazy val elasticClient: ElasticClient = new ElasticClient(jestClient)
  }
}
