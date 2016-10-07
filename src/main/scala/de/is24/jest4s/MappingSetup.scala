package de.is24.jest4s

import de.is24.jest4s.utils.SLF4JLogging
import io.searchbox.client.JestResult

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object MappingSetup extends SLF4JLogging {

  def perform(elasticClient: ElasticClient, mappings: Seq[IndexMapping])(implicit ec: ExecutionContext): Future[Unit] = {
    Future.sequence(mappings.map {
      indexMapping =>
        for {
          _ ← createIndex(elasticClient, indexMapping.indexName)
          _ ← createMapping(elasticClient, indexMapping)
        } yield ()
    }).map(_ ⇒ ())
  }

  private def createIndex(elasticClient: ElasticClient, indexName: IndexName)(implicit ec: ExecutionContext): Future[Unit] =
    elasticClient
      .createIndex(indexName)
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
