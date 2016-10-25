package de.is24.jest4s

import de.is24.jest4s.utils.SLF4JLogging
import io.searchbox.client.JestResult

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object MappingSetup extends SLF4JLogging {

  def perform(elasticClient: ElasticClient, indexOptions: Seq[IndexOptions])(implicit ec: ExecutionContext): Future[Unit] = {

    Future.sequence(indexOptions.map { indexOptions =>
      for {
        _ ← createIndex(elasticClient, indexOptions.indexMapping.indexName, indexOptions.indexSettings)
        _ ← createMapping(elasticClient, indexOptions.indexMapping)
      } yield ()
    }).map(_ ⇒ ())
  }

  private def createIndex(elasticClient: ElasticClient, indexName: IndexName, indexSettings: Option[IndexSettings])(implicit ec: ExecutionContext): Future[Unit] =
    elasticClient
      .createIndex(indexName, indexSettings)
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
