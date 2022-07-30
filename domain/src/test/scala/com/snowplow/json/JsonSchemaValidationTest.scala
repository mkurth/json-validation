package com.snowplow.json

import cats.Id
import cats.data.EitherT
import com.snowplow.json.JsonSchemaRegistry.{GeneralPersistenceError, LoadJsonSchema, SchemaNotFound}
import com.snowplow.json.JsonSchemaValidation.{
  validateJsonSchema,
  GeneralValidationError,
  InvalidJson,
  SchemaDoesNotExist,
  ValidJsonSchema
}
import org.scalatest.GivenWhenThen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonSchemaValidationTest extends AnyFlatSpec with Matchers with GivenWhenThen {

  private val exampleSchema =
    """
      |{
      |  "$schema": "http://json-schema.org/draft-04/schema#",
      |  "type": "object",
      |  "properties": {
      |    "source": {
      |      "type": "string"
      |    },
      |    "destination": {
      |      "type": "string"
      |    },
      |    "timeout": {
      |      "type": "integer",
      |      "minimum": 0,
      |      "maximum": 32767
      |    },
      |    "chunks": {
      |      "type": "object",
      |      "properties": {
      |        "size": {
      |          "type": "integer"
      |        },
      |        "number": {
      |          "type": "integer"
      |        }
      |      },
      |      "required": ["size"]
      |    }
      |  },
      |  "required": ["source", "destination"]
      |}
      |""".stripMargin

  behavior of "JsonSchemaValidation"

  it should "return a not found for not existing schemas" in {
    val emptyRegistry: LoadJsonSchema[Id] = id => EitherT.leftT(SchemaNotFound(id))

    val result = validateJsonSchema[Id](emptyRegistry)("anything", "{}").value

    result shouldBe Left(SchemaDoesNotExist("anything"))
  }

  it should "return a general validation error if something goes wrong with the storage" in {
    val faultyRegistry: LoadJsonSchema[Id] = id => EitherT.leftT(GeneralPersistenceError(id))

    val Left(result) = validateJsonSchema[Id](faultyRegistry)("anything", "{}").value

    result shouldBe a[GeneralValidationError]
  }

  it should "return a general validation error if the stored schema is not valid json" in {
    val invalidJsonRegistry: LoadJsonSchema[Id] = id => EitherT.rightT(JsonSchema(id, "<this> is no </json>"))

    val Left(result) = validateJsonSchema[Id](invalidJsonRegistry)("anything", "{}").value

    result shouldBe a[GeneralValidationError]
  }

  it should "return invalid json, if the provided json is not valid" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id => EitherT.rightT(JsonSchema(id, "{}"))

    val Left(result) = validateJsonSchema[Id](validJsonRegistry)("anything", "<this> is no </json>").value

    result shouldBe InvalidJson
  }

  it should "validate json" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id =>
      EitherT.rightT(
        JsonSchema(
          id,
          exampleSchema
        )
      )

    val jsonToCheck   =
      """
        |{
        |  "source": "/home/alice/image.iso",
        |  "destination": "/mnt/storage",
        |  "chunks": {
        |    "size": 1024
        |  }
        |}
        |""".stripMargin
    val Right(result) = validateJsonSchema[Id](validJsonRegistry)("anything", jsonToCheck).value

    result shouldBe ValidJsonSchema("anything")
  }

  it should "remove keys with null values" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id =>
      EitherT.rightT(
        JsonSchema(
          id,
          exampleSchema
        )
      )

    val jsonToCheck   =
      """
        |{
        |  "source": "/home/alice/image.iso",
        |  "destination": "/mnt/storage",
        |  "timeout": null,
        |  "chunks": {
        |    "size": 1024,
        |    "number": null
        |  }
        |}
        |""".stripMargin
    val Right(result) = validateJsonSchema[Id](validJsonRegistry)("anything", jsonToCheck).value

    result shouldBe ValidJsonSchema("anything")
  }

}
