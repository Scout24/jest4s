package de.is24.jest4s.utils

import org.slf4j.{ Logger, LoggerFactory }

trait SLF4JLogging {

  lazy val log: Logger = LoggerFactory.getLogger(getClass)
}
