package de.is24.jest4s

import java.net.URI
import java.nio.file.Files
import java.util
import java.util.UUID

import de.is24.jest4s.utils.SLF4JLogging
import org.apache.commons.io.FileUtils
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.node.{ InternalSettingsPreparer, Node }
import org.elasticsearch.plugins.Plugin
import org.elasticsearch.transport.Netty3Plugin

import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.util.control.NonFatal

trait EmbeddedElasticSearchSupport extends SLF4JLogging {

  implicit def executionContext: ExecutionContext

  private lazy val clusterId = UUID.randomUUID().toString
  private lazy val clusterName = "embedded-elasticsearch-$clusterId"
  private lazy val dataDir = Files.createTempDirectory(s"${clusterName}_data").toFile
  private lazy val settings = Settings.builder
    .put("path.home", dataDir.toString)
    .put("path.data", dataDir.toString)
    .put("cluster.name", clusterName)
    .put("http.enabled", true)
    .put("transport.type", "local")
    .put("http.type", "netty3")
    .build

  private lazy val node = new PluginConfigurableNode(settings, util.Arrays.asList(classOf[Netty3Plugin]))

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

  private class PluginConfigurableNode(settings: Settings, classpathPlugins: util.Collection[Class[_ <: Plugin]]) extends Node(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins)

}
