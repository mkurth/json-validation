package com.snowplow.json

import cats.Id
import cats.data.EitherT
import com.snowplow.json.JsonSchemaRegistry.*
import com.snowplow.json.JsonSchemaValidation.*
import com.snowplow.json.RegistrationError.*
import com.snowplow.json.PersistenceError.*
import com.snowplow.json.ValidationError.*
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
    val emptyRegistry: LoadJsonSchema[Id] = id => SchemaNotFound(id)

    val result = validateJsonSchema[Id](emptyRegistry)("anything", "{}")

    result shouldBe SchemaDoesNotExist("anything")
  }

  it should "return a general validation error if something goes wrong with the storage" in {
    val faultyRegistry: LoadJsonSchema[Id] = id => GeneralPersistenceError(id)

    val result = validateJsonSchema[Id](faultyRegistry)("anything", "{}")

    result shouldBe a[GeneralValidationError]
  }

  it should "return a general validation error if the stored schema is not valid json" in {
    val invalidJsonRegistry: LoadJsonSchema[Id] = id => JsonSchema(id, "<this> is no </json>")

    val result = validateJsonSchema[Id](invalidJsonRegistry)("anything", "{}")

    result shouldBe a[GeneralValidationError]
  }

  it should "return invalid json, if the provided json is not valid" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id => JsonSchema(id, "{}")

    val result = validateJsonSchema[Id](validJsonRegistry)("anything", "<this> is no </json>")

    result shouldBe ValidationError.InvalidJson
  }

  it should "validate json" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id => JsonSchema(id, exampleSchema)

    val jsonToCheck =
      """
        |{
        |  "source": "/home/alice/image.iso",
        |  "destination": "/mnt/storage",
        |  "chunks": {
        |    "size": 1024
        |  }
        |}
        |""".stripMargin
    val result      = validateJsonSchema[Id](validJsonRegistry)("anything", jsonToCheck)

    result shouldBe ValidJsonSchema("anything")
  }

  it should "remove keys with null values" in {
    val validJsonRegistry: LoadJsonSchema[Id] = id => JsonSchema(id, exampleSchema)

    val jsonToCheck =
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
    val result      = validateJsonSchema[Id](validJsonRegistry)("anything", jsonToCheck)

    result shouldBe ValidJsonSchema("anything")
  }

}
