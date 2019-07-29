package io.applicative.datastore

import com.google.cloud.datastore.{Transaction, Datastore => CloudDatastore}

import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

trait Datastore[F[_]] {
  /**
    * Creates a new Key with automatically randomly generated id.
    * @tparam E type of entity. Must be always specified.
    */
  def newKey[E <: BaseEntity : TypeTag : ClassTag](): F[Key]

  /**
    * Creates a new Key with specified name.
    * @tparam E type of entity. Must be always specified.
    */
  def newKey[E <: BaseEntity : TypeTag : ClassTag](name: String): F[Key]

  /**
    * Creates a new Key with specified id.
    * @tparam E type of entity. Must be always specified.
    */
  def newKey[E <: BaseEntity : TypeTag : ClassTag](id: Long): F[Key]

  /**
    * Returns a new Datastore transaction.
    */
  def newTransaction: F[Transaction]

  /**
    * Datastore add operation: inserts the provided entity.
    *
    * If an entity with similar id does not exists, entity is inserted.
    * Otherwise, future fails because of DatastoreException.
    *
    * @param entity instance of type E to be inserted.
    * @tparam E type of entity. Must be always specified.
    */
  def add[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[E]

  /**
    * Datastore add operation: inserts the provided entity with its key.
    *
    * If an entity with similar id does not exists, entity is inserted.
    * Otherwise, future fails because of DatastoreException.
    *
    * @param entity instance of type E to be inserted.
    * @param key Key
    * @tparam E type of entity. Must be always specified.
    */
  def add[E <: BaseEntity : TypeTag : ClassTag](key: Key, entity: E): F[E]

  /**
    * Datastore add operation: inserts the provided entities along with its keys.
    *
    * If an entity with similar id does not exists, entity is inserted.
    * Otherwise, future fails because of DatastoreException.
    *
    * @param ke map of entity and its key
    * @tparam E type of entity. Must be always specified.
    */
  def add[E <: BaseEntity : TypeTag : ClassTag](ke: Map[Key, E]): F[List[E]]

  /**
    * A Datastore update operation. The operation will fail if an entity with the same id does not
    * already exist.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def update[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[Unit]

  /**
    * A Datastore update operation. The operation will fail if an entity with the same id does not
    * already exist.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def update[E <: BaseEntity : TypeTag : ClassTag](entities: List[E]): F[Unit]

  /**
    * A Datastore put (a.k.a upsert) operation: inserts an entity if it does not exist, updates it
    * otherwise.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def put[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[E]

  /**
    * A Datastore put (a.k.a upsert) operation: inserts an entity if it does not exist, updates it
    * otherwise.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def put[E <: BaseEntity : TypeTag : ClassTag](entities: List[E]): F[List[E]]

  /**
    * A datastore delete operation.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def delete[E <: BaseEntity : TypeTag : ClassTag](keys: Key*): F[Unit]

  /**
    * A datastore delete operation.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def delete[E <: BaseEntity : TypeTag : ClassTag](ids: List[Long]): F[Unit]

  /**
    * Retrieves instance of class E with specified id.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def get[E <: BaseEntity : TypeTag : ClassTag](id: Long): F[Option[E]]

  /**
    * Retrieves instance of class E with specified id.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def get[E <: BaseEntity : TypeTag : ClassTag](id: String): F[Option[E]]

  /**
    * Retrieves instance of class E with specified key.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def get[E <: BaseEntity : TypeTag : ClassTag](key: Key): F[Option[E]]

  /**
    * Returns an Entity for each given id that exists in the Datastore. The order of
    * the result is unspecified. Results are loaded lazily.
    *
    * @tparam E type of entity. Must be always specified.
    */
  def getLazy[E <: BaseEntity : TypeTag : ClassTag, K](ids: List[K]): F[Iterator[E]]

  /**
    * Returns a list with a value for each given key (ordered by input).
    *
    * @tparam E type of entity. Must be always specified.
    */
  def fetch[E <: BaseEntity : TypeTag : ClassTag, K](ids: List[K]): F[List[Option[E]]]

  private[datastore] def getKindByClass(clazz: Class[_]): String

}
