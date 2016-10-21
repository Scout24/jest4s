package de.is24.jest4s

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.Duration
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import io.searchbox.action.Action
import io.searchbox.client.{ JestClient, JestResult }
import io.searchbox.core._
import io.searchbox.core.SearchScroll.Builder
import io.searchbox.indices.{ CreateIndex, Refresh }
import io.searchbox.indices.mapping.PutMapping
import io.searchbox.params.Parameters
import play.api.libs.json._
import PromiseJestResultHandler._
import utils.SLF4JLogging
import Protocol._

class ElasticSearchException(message: String) extends RuntimeException(message)

class ElasticClient(jestClient: JestClient)(implicit val ec: ExecutionContext, mat: Materializer) extends SLF4JLogging
    with SchemaMethods
    with ScrollMethods {

  def shutdown(): Unit =
    jestClient.shutdownClient()

  def upsertDocument[T: Writes](id: ElasticSearchId, document: T, elasticType: ElasticType, indexName: IndexName): Future[JestResult] =
    execute(
      insertDocumentBuilder(document, elasticType, indexName)
        .id(id.elasticSearchId)
        .build()
    )

  def bulkInsertOrUpdate[T: Writes](documents: Seq[(ElasticSearchId, T)], elasticType: ElasticType, indexName: IndexName): Future[JestResult] = {
    val bulkBuilder = new Bulk.Builder()
      .defaultIndex(indexName.indexName)
      .defaultType(elasticType.typeName)
    documents.foreach {
      case (id, document) ⇒
        val insertAction = new Index.Builder(Json.stringify(Json.toJson(document))).id(id.elasticSearchId).build()
        bulkBuilder.addAction(insertAction)
    }
    execute(bulkBuilder.build())
  }

  def getDocument[T: Reads](id: ElasticSearchId, elasticType: ElasticType, indexName: IndexName): Future[Option[T]] =
    execute(new Get.Builder(indexName.indexName, id.elasticSearchId).`type`(elasticType.typeName).build())
      .map(_.getSourceAsString)
      .map(Json.parse)
      .map {
        _.validate[T] match {
          case JsSuccess(t, _) ⇒
            Some(t)
          case e: JsError ⇒
            log.error(s"Could not parse document from elasticsearch: ${e.errors}")
            None
        }
      }
      .recover {
        case e: ElasticSearchException if e.getMessage.contains("404 Not Found") ⇒
          None
      }

  def search[T: Reads](indexName: IndexName, query: JsValue): Future[Seq[T]] =
    execute(new Search.Builder(Json.stringify(query))
      .addIndex(indexName.indexName)
      .build()).map(_.getJsonString)
      .map(Json.parse)
      .map(_ \ "hits" \ "hits")
      .flatMap(_.validate[Seq[Hit[T]]] match {
        case JsSuccess(hits, _) ⇒
          Future.successful(hits.map(_.source))
        case e: JsError ⇒
          val message: String = s"Could not parse documents from elastic search: ${e.errors}"
          log.error(message)
          Future.failed(new Exception(message))
      })

  protected def execute[T <: JestResult](request: Action[T]): Future[T] = {
    jestClient.executeAsyncPromise(request)
  }

  private def insertDocumentBuilder[T: Writes](document: T, elasticType: ElasticType, indexName: IndexName): Index.Builder =
    new Index.Builder(Json.stringify(Json.toJson(document)))
      .index(indexName.indexName)
      .`type`(elasticType.typeName)
}

sealed trait JestMethods {
  implicit val ec: ExecutionContext
  protected def execute[T <: JestResult](request: Action[T]): Future[T]
}

sealed trait SchemaMethods extends JestMethods with SLF4JLogging {

  def createIndex(indexName: IndexName): Future[JestResult] =
    execute(
      new CreateIndex.Builder(indexName.indexName)
        .build()
    )

  // Use this carefully, it comes at a high performance cost
  def refreshIndex(indexNames: IndexName*): Future[JestResult] = {
    val refresh = indexNames.foldLeft(new Refresh.Builder()) { (builder, indexName) ⇒
      builder.addIndex(indexName.indexName)
    }.build()
    execute(refresh)
  }

  def createMapping(indexName: IndexName, typeName: ElasticType, mappingBody: JsValue): Future[JestResult] =
    execute(
      new PutMapping.Builder(indexName.indexName, typeName.typeName, Json.stringify(mappingBody))
        .build()
    )

}

sealed trait ScrollMethods extends JestMethods with SLF4JLogging {

  private implicit class ScrollLifetimeFormatter(lifetime: Duration) {
    def asScrollLifetime: String = s"${lifetime.toMillis}ms"
  }

  def searchWithScroll[T: Reads](indexName: IndexName, query: JsValue, searchContextLifetime: Duration): Source[T, NotUsed] = {

    def retrieveFirstOrSubsequentBatch(scrollId: String): Future[ScrollBatchResult[T]] = {
      if (scrollId.isEmpty) {
        searchWithScrollAndRetrieveFirstBatch[T](indexName: IndexName, query, searchContextLifetime)
      } else {
        retrieveNextScrollBatch[T](scrollId, searchContextLifetime)
      }
    }

    Source
      .unfoldAsync("") { scrollId ⇒
        retrieveFirstOrSubsequentBatch(scrollId)
          .map { scrollBatchResult: ScrollBatchResult[T] ⇒
            Option(scrollBatchResult)
              .filterNot(_.results.isEmpty)
              .map(s ⇒ s.scrollId → s.results)
          }
      }
      .mapConcat[T](identity)
  }

  private def searchWithScrollAndRetrieveFirstBatch[T: Reads](indexName: IndexName, query: JsValue, searchContextLifetime: Duration): Future[ScrollBatchResult[T]] = {
    extractScrollBatchResult[T](execute(
      new Search.Builder(Json.stringify(query))
        .addIndex(indexName.indexName)
        .setParameter(Parameters.SCROLL, searchContextLifetime.asScrollLifetime)
        .build()
    ))
  }

  private def retrieveNextScrollBatch[T: Reads](scrollId: String, searchContextLifetime: Duration): Future[ScrollBatchResult[T]] =
    extractScrollBatchResult[T](execute(new Builder(scrollId, searchContextLifetime.asScrollLifetime).build()))

  private def extractScrollBatchResult[T: Reads](jestResult: Future[JestResult]): Future[ScrollBatchResult[T]] = {
    jestResult.map(_.getJsonString)
      .map(Json.parse)
      .flatMap(_.validate[ScrollBatchResult[T]] match {
        case JsSuccess(scrollBatchResult, _) ⇒
          Future.successful(scrollBatchResult)
        case e: JsError ⇒
          val message: String = s"Could not parse documents from elastic search: ${e.errors}"
          log.error(message)
          Future.failed(new Exception(message))
      })
  }
}
