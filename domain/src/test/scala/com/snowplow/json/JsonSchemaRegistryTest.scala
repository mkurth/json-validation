package com.snowplow.json

import cats.Id
import cats.data.EitherT
import com.snowplow.json.JsonSchemaRegistry._
import org.scalatest.GivenWhenThen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable

class JsonSchemaRegistryTest extends AnyFlatSpec with Matchers with GivenWhenThen {

  behavior of "JsonSchemaRegistry - registerSchema"

  it should "return an error if the content is not valid json" in {
    val inMemoryPersistence = new InMemoryPersistence()
    val persistence         = inMemoryPersistence.persist
    val load                = inMemoryPersistence.load

    registerJsonSchema[Id](persistence, load)(JsonSchema("invalid json", "this is not json")).value shouldBe Left(
      InvalidJson
    )
  }

  it should "return a duplicate key error if the another schema is being registered with the same id" in {
    Given("an in memory storage")
    val inMemoryPersistence = new InMemoryPersistence()
    val persistence         = inMemoryPersistence.persist
    val load                = inMemoryPersistence.load
    val register            = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    register(JsonSchema("valid_json", "{}")).value shouldBe Right(JsonSchemaRegistered("valid_json"))
    And("registering a different schema under the same key")
    val result = register(JsonSchema("valid_json", """{ "type": "string" }""")).value

    Then("we expect a duplicate error")
    result shouldBe Left(JsonSchemaAlreadyExists("valid_json"))
  }

  it should "return a general error if the persistence had an error" in {
    Given("an always failing persistence")
    val persistence: PersistJsonSchema[Id] = _ => EitherT.leftT(GeneralPersistenceError("random db error"))
    val load: LoadJsonSchema[Id]           = id => EitherT.leftT(SchemaNotFound(id))
    val register                           = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    val result = register(JsonSchema("valid_json", "{}")).value

    Then("we expect a general error")
    result shouldBe Left(GeneralRegistrationError("valid_json", "random db error"))
  }

  it should "also return a general error if an error occurred while loading" in {
    Given("an always failing persistence")
    val persistence: PersistJsonSchema[Id] = schema => EitherT.leftT(SchemaNotFound(schema.id))
    val load: LoadJsonSchema[Id]           = id => EitherT.leftT(GeneralPersistenceError("something went wrong"))
    val register                           = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    val result = register(JsonSchema("valid_json", "{}")).value

    Then("we expect a general error")
    result shouldBe Left(GeneralRegistrationError("valid_json", "something went wrong"))
  }

}

class InMemoryPersistence(state: mutable.Map[String, JsonSchema] = mutable.Map.empty) {
  def persist: PersistJsonSchema[Id] = (jsonSchema: JsonSchema) =>
    if (state.keys.exists(_ == jsonSchema.id)) EitherT.leftT(SchemaAlreadyExists(jsonSchema.id))
    else {
      state.put(jsonSchema.id, jsonSchema)
      EitherT.rightT(JsonSchemaPersisted(jsonSchema.id))
    }

  def load: LoadJsonSchema[Id] = id =>
    state.get(id) match {
      case Some(value) => EitherT.rightT(value)
      case None        => EitherT.leftT(SchemaNotFound(id))
    }
}
