package de.is24.jest4s

case class IndexMapping(indexName: IndexName, elasticType: ElasticType, mapping: Mapping)

case class NumberOfShards(number: Int) extends AnyVal
case class NumberOfReplica(number: Int) extends AnyVal
case class IndexSettings(numberOfShards: NumberOfShards, numberOfReplicas: NumberOfReplica)

case class IndexOptions(indexMapping: IndexMapping, indexSettings: Option[IndexSettings])
