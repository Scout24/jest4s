package de.is24.jest4s

import de.is24.jest4s.utils.SLF4JLogging
import io.searchbox.client.JestResult

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object MappingSetup extends SLF4JLogging {

  def perform(elasticClient: ElasticClient, mappings: Seq[IndexMapping], settings: Seq[IndexSettings])(implicit ec: ExecutionContext): Future[Unit] = {
    require(mappings.size == settings.size, s"Number of index mappings(${mappings.size}) does not match number of index settings(${settings.size}).")

    Future.sequence(mappings.zip(settings).map {
      case (indexMapping, indexSettings) =>
        for {
          _ ← createIndex(elasticClient, indexMapping.indexName, indexSettings)
          _ ← createMapping(elasticClient, indexMapping)
        } yield ()
    }).map(_ ⇒ ())
  }

  private def createIndex(elasticClient: ElasticClient, indexName: IndexName, indexSettings: IndexSettings)(implicit ec: ExecutionContext): Future[Unit] =
    elasticClient
      .createIndex(indexName, indexSettings.numberOfShards.number, indexSettings.numberOfReplicas.number)
      .map(_ ⇒ ())
      .recoverWith {
        case NonFatal(exception) ⇒
          log.info(s"Error creating index, probably (?!) because it already exists", exception)
          Future.successful(())
      }

  private def createMapping(elasticClient: ElasticClient, mapping: IndexMapping): Future[JestResult] = {
    elasticClient.createMapping(mapping.indexName, mapping.elasticType, mapping.mapping)
  }
}
