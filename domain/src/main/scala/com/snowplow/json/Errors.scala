package com.snowplow.json

enum RegistrationError:
  case JsonSchemaAlreadyExists(id: String)
  case GeneralRegistrationError(id: String, error: String)
  case ConcurrentWritesError(id: String, error: String)
  case InvalidJson

enum PersistenceError:
  case SchemaNotFound(id: String)
  case SchemaAlreadyExists(id: String)
  case GeneralPersistenceError(error: String)

enum ValidationError:
  case InvalidJson
  case JsonDoesNotMatchSchema(id: String, errors: List[String])
  case GeneralValidationError(id: String, error: String)
  case SchemaDoesNotExist(id: String)
