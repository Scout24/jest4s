package de.is24.jest4s

import java.net.URI
import java.nio.file.Files
import java.util.UUID
import org.apache.commons.io.FileUtils
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder
import play.api.inject.ApplicationLifecycle
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.blocking
import scala.util.control.NonFatal
import utils.SLF4JLogging

trait EmbeddedElasticSearchSupport extends SLF4JLogging {

  implicit def executionContext: ExecutionContext

  private lazy val clusterId = UUID.randomUUID().toString
  private lazy val clusterName = "embedded-elasticsearch-$clusterId"
  private lazy val dataDir = Files.createTempDirectory(s"${clusterName}_data").toFile
  private lazy val settings = ImmutableSettings.settingsBuilder
    .put("path.data", dataDir.toString)
    .put("cluster.name", clusterName)
    .put("node.http.enabled", true)
    .put("index.store.type", "memory")
    .put("index.number_of_shards", 1)
    .put("index.number_of_replicas", 0)
    .build

  private lazy val node = NodeBuilder.nodeBuilder().local(true).settings(settings).build

  def startEmbeddedElasticSearch(): ElasticSearchHttpUri = {
    node.start()
    val uri = ElasticSearchHttpUri(new URI("http://localhost:9200"))
    log.info(s"Embedded elasticsearch starting at $uri")
    uri
  }

  def stopEmbeddedElasticSearch(): Future[Unit] = {
    Future {
      blocking {
        node.close()
        try {
          FileUtils.forceDelete(dataDir)
        } catch {
          case NonFatal(e) ⇒
            log.warn(s"Failed to cleanup elasticsearch data at $dataDir", e)
        }
      }
    }
  }
}
