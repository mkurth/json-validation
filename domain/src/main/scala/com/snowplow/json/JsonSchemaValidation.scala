package com.snowplow.json

import cats.data.EitherT
import com.snowplow.json.JsonSchemaRegistry.{LoadJsonSchema, PersistenceError}

object JsonSchemaValidation {

  sealed trait ValidationError
  case object InvalidJson                                                   extends ValidationError
  final case class JsonDoesNotMatchSchema(id: String, errors: List[String]) extends ValidationError

  final case class ValidJsonSchema(id: String)

  def validateJsonSchema[F[_]](
      loadJsonSchema: LoadJsonSchema[F]
  )(id: String, jsonToValidate: String): EitherT[F, ValidationError, ValidJsonSchema] = ???

}
