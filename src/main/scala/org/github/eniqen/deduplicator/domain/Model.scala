package org.github.eniqen.deduplicator.domain

import bloomfilter.CanGenerateHashFrom
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

/**
  * @author Mikhail Nemenko { @literal <nemenkoma@gmail.com>}
  */

final case class Model(name: String, value: Long)

object Model {
  implicit val modelDecoder: Decoder[Model] = Decoder.forProduct2("event_name", "value")(Model.apply)
  implicit val modelEncoder: Encoder[Model] =
    Encoder.forProduct2(nameA0 = "event_name", nameA1 = "value")(m => m.name -> m.value)

  implicit def hashFrom[M: Encoder](implicit H: CanGenerateHashFrom[String]): CanGenerateHashFrom[Model] =
    (m: Model) => H.generateHash(m.asJson.dropNullValues.noSpaces)
}

