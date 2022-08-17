package com.snowplow.json

import cats.Id
import cats.data.EitherT
import cats.effect.IO
import com.snowplow.json.JsonSchemaRegistry.*
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

    registerJsonSchema[Id](persistence, load)(JsonSchema("invalid json", "this is not json")) shouldBe InvalidJson
  }

  it should "return a duplicate key error if the another schema is being registered with the same id" in {
    Given("an in memory storage")
    val inMemoryPersistence = new InMemoryPersistence()
    val persistence         = inMemoryPersistence.persist
    val load                = inMemoryPersistence.load
    val register            = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    register(JsonSchema("valid_json", "{}")) shouldBe JsonSchemaRegistered("valid_json")
    And("registering a different schema under the same key")
    val result = register(JsonSchema("valid_json", """{ "type": "string" }"""))

    Then("we expect a duplicate error")
    result shouldBe JsonSchemaAlreadyExists("valid_json")
  }

  it should "return a general error if the persistence had an error" in {
    Given("an always failing persistence")
    val persistence: PersistJsonSchema[Id] = _ => GeneralPersistenceError("random db error")
    val load: LoadJsonSchema[Id]           = id => SchemaNotFound(id)
    val register                           = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    val result = register(JsonSchema("valid_json", "{}"))

    Then("we expect a general error")
    result shouldBe GeneralRegistrationError("valid_json", "random db error")
  }

  it should "also return a general error if an error occurred while loading" in {
    Given("an always failing persistence")
    val persistence: PersistJsonSchema[Id] = schema => SchemaNotFound(schema.id)
    val load: LoadJsonSchema[Id]           = id => GeneralPersistenceError("something went wrong")
    val register                           = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    val result = register(JsonSchema("valid_json", "{}"))

    Then("we expect a general error")
    result shouldBe GeneralRegistrationError("valid_json", "something went wrong")
  }

  it should "return a concurrent error if an error occurred while persisting" in {
    Given("an always failing persistence")
    val persistence: PersistJsonSchema[Id] = schema => SchemaAlreadyExists(schema.id)
    val load: LoadJsonSchema[Id]           = id => SchemaNotFound(id)
    val register                           = registerJsonSchema[Id](persistence, load) _

    When("registering a valid_json schema")
    val result = register(JsonSchema("valid_json", "{}"))

    Then("we expect a general error")
    result shouldBe a[ConcurrentWritesError]
  }

}

class InMemoryPersistence(state: mutable.Map[String, JsonSchema] = mutable.Map.empty) {
  def persist: PersistJsonSchema[Id] = (jsonSchema: JsonSchema) =>
    if (state.keys.exists(_ == jsonSchema.id)) SchemaAlreadyExists(jsonSchema.id)
    else {
      state.put(jsonSchema.id, jsonSchema)
      JsonSchemaPersisted(jsonSchema.id)
    }

  def load: LoadJsonSchema[Id] = id =>
    state.get(id) match {
      case Some(value) => value
      case None        => SchemaNotFound(id)
    }
}
