package de.is24.jest4s

import play.api.libs.json._
import play.api.libs.functional.syntax._

object Protocol {

  case class ScrollBatchHit[T](source: T)

  implicit def scrollBatchHitReads[T: Reads]: Reads[ScrollBatchHit[T]] = {
    (JsPath \ "_source").read[T].map(t ⇒ ScrollBatchHit(t))
  }

  implicit def scrollBatchResultReads[T: Reads]: Reads[ScrollBatchResult[T]] = {
    (
      (JsPath \ "_scroll_id").read[String] and
      (JsPath \ "hits" \ "hits").read[List[ScrollBatchHit[T]]]
    ) { (scrollId: String, hits: List[ScrollBatchHit[T]]) ⇒
        ScrollBatchResult(scrollId, hits.map(_.source))
      }
  }
}
