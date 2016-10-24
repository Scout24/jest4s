package de.is24.jest4s

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import org.specs2.specification.{ BeforeAfterAll, Scope }
import play.api.libs.json.{ Format, Json }
import play.api.test.{ DefaultAwaitTimeout, FutureAwaits }
import net.maffoo.jsonquote.play._

class ElasticClientSpec extends StatefulElasticSpec {

  implicit val someDocumentFormat: Format[SomeDocument] = Json.format[SomeDocument]

  case class SomeDocument(someField: Long)

  val givenDocument = SomeDocument(42L)
  val givenID = ElasticSearchId("givenID")

  val testIndex = IndexName("testindex")
  val testIndexWithSettings = IndexName("testindex-with-settings")
  val testType = ElasticType("testtype")

  sequential

  "A elastic client" should {

    "create an index" in new WithElasticClient {
      val result = await(elasticClient.createIndex(testIndex))
      result.getJsonString must be equalTo """{"acknowledged":true}"""
    }

    "create an index with custom settings" in new WithElasticClient {
      await(elasticClient.createIndex(testIndexWithSettings, 3, 1))
      val indexSettings = await(elasticClient.getSettings(testIndexWithSettings))

      val settingValues = indexSettings
        .getJsonObject
        .getAsJsonObject(testIndexWithSettings.indexName)
        .getAsJsonObject("settings")
        .getAsJsonObject("index")

      settingValues.getAsJsonPrimitive("number_of_shards").getAsString must be equalTo "3"
      settingValues.getAsJsonPrimitive("number_of_replicas").getAsString must be equalTo "1"
    }

    "create a mapping" in new WithElasticClient {
      val mappingBody =
        json"""{
            ${testType.typeName} : {
              "properties": {
                "someField": {
                  "type": "long"
                }
              }
            }
          }"""
      await(MappingSetup.perform(elasticClient, Seq(IndexMapping(testIndex, testType, mappingBody))))
    }

    "persist a document" in new WithElasticClient {
      await(elasticClient.upsertDocument(givenID, givenDocument, testType, testIndex))
    }

    "retrieve an existing document by search ID" in new WithElasticClient {
      await(elasticClient.refreshIndex(testIndex))
      val maybeGivenDocument = await(elasticClient.getDocument[SomeDocument](givenID, testType, testIndex))

      maybeGivenDocument must beSome(givenDocument)
    }

    "return None when searching for non-existing search IDs" in new WithElasticClient {
      val emptyResult = await(elasticClient.getDocument[SomeDocument](ElasticSearchId("nonExistingID"), testType, testIndex))

      emptyResult must be(None)
    }

    "update an existing document" in new WithElasticClient {
      val updatedDocument = SomeDocument(43L)
      await(elasticClient.upsertDocument(givenID, updatedDocument, testType, testIndex))
      await(elasticClient.refreshIndex(testIndex))
      val maybeUpdatedDocument = await(elasticClient.getDocument[SomeDocument](givenID, testType, testIndex))

      maybeUpdatedDocument must beSome(updatedDocument)
    }

    "retrieve batches of documents by query string" in new WithElasticClient {
      val maxFieldValue = 333L
      val documentsWithId = Seq(
        111L → "A",
        222L → "B",
        333L → "C",
        444L → "D"
      )

      await(Future.sequence(documentsWithId.map {
        case (field, id) ⇒
          elasticClient.upsertDocument(ElasticSearchId(id), SomeDocument(field), testType, testIndex)
      }))
      await(elasticClient.refreshIndex(testIndex))

      val query =
        json"""{
               "size": 2,
               "query": {
                  "match_all": {}
               },
               "filter": {
                 "bool": {
                   "must": {
                     "range": {
                       "someField": { "gte": 44, "lte": $maxFieldValue }
                     }
                   }
                 }
               }
            }"""
      val scrollBatchResult = await(elasticClient
        .searchWithScroll[SomeDocument](testIndex, query, searchContextLifetime = 3.minutes)
        .runWith(collectingSink[SomeDocument, Seq]))
      val (matchingDocuments, notMatchingDocuments) = documentsWithId
        .map(_._1)
        .map(SomeDocument.apply)
        .partition(_.someField <= maxFieldValue)

      scrollBatchResult must containAllOf(matchingDocuments)
      notMatchingDocuments.foreach { notMatching ⇒
        scrollBatchResult must not contain notMatching
      }
    }
  }

  "retrieve one batch of document by query string" in new WithElasticClient {
    val maxFieldValue = 333L
    val documentsWithId = Seq(
      111L → "A",
      222L → "B",
      333L → "C",
      444L → "D"
    )

    await(Future.sequence(documentsWithId.map {
      case (field, id) ⇒
        elasticClient.upsertDocument(ElasticSearchId(id), SomeDocument(field), testType, testIndex)
    }))
    await(elasticClient.refreshIndex(testIndex))

    val query =
      json"""{
               "size": 3,
               "query": {
                  "match_all": {}
               },
               "filter": {
                 "bool": {
                   "must": {
                     "range": {
                       "someField": { "gte": 44, "lte": $maxFieldValue }
                     }
                   }
                 }
               }
            }"""
    val scrollBatchResult = await(elasticClient.search[SomeDocument](testIndex, query))
    val (matchingDocuments, notMatchingDocuments) = documentsWithId
      .map(_._1)
      .map(SomeDocument.apply)
      .partition(_.someField <= maxFieldValue)

    scrollBatchResult must containAllOf(matchingDocuments)
    notMatchingDocuments.foreach { notMatching ⇒
      scrollBatchResult must not contain notMatching
    }
  }
}
