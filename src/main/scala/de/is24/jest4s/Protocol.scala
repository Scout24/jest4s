package de.is24.jest4s

import play.api.libs.json._
import play.api.libs.functional.syntax._

object Protocol {

  implicit def scrollBatchHitReads[T: Reads]: Reads[Hit[T]] = {
    (JsPath \ "_source").read[T].map(t ⇒ Hit(t))
  }

  implicit def scrollBatchResultReads[T: Reads]: Reads[ScrollBatchResult[T]] = {
    (
      (JsPath \ "_scroll_id").read[String] and
      (JsPath \ "hits" \ "hits").read[List[Hit[T]]]
    ) { (scrollId: String, hits: List[Hit[T]]) ⇒
        ScrollBatchResult(scrollId, hits.map(_.source))
      }
  }
}
