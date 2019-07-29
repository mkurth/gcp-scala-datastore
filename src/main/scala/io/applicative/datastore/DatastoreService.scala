package io.applicative.datastore

import cats.Functor
import cats.effect.{ExitCase, IO, Sync}
import cats.syntax.functor._
import com.google.auth.Credentials
import com.google.cloud.datastore.{DatastoreOptions, DatastoreReader, Entity, EntityQuery, KeyFactory, Transaction, Datastore => CloudDataStore, Key => CloudKey}
import io.applicative.datastore.exception.UnsupportedIdTypeException
import io.applicative.datastore.util.reflection.{Kind, ReflectionHelper}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.higherKinds
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

object DatastoreService {

  implicit val futureSync: Sync[Future] = new Sync[Future] {
    override def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] = fa.recoverWith[A]({
      case e => f(e)
    })
    override def pure[A](x: A): Future[A] = Future.successful(x)
    override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
    override def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] = f(a).flatMap({
      case Right(value) => pure(value)
      case Left(value) => tailRecM(value)(f)
    })
    override def bracketCase[A, B](acquire: Future[A])(use: A => Future[B])(release: (A, ExitCase[Throwable]) => Future[Unit]): Future[B] = {
      acquire.flatMap(use)
    }
    override def raiseError[A](e: Throwable): Future[A] = Future.failed[A](e)
    override def suspend[A](thunk: => Future[A]): Future[A] = thunk
  }

  lazy val default: Datastore[IO] = new DatastoreService[IO](DatastoreOptions.getDefaultInstance.getService)
  lazy val defaultFuture: Datastore[Future] = new DatastoreService[Future](DatastoreOptions.getDefaultInstance.getService)

  def apply[F[_] : Sync : Functor](
                                    projectId: String,
                                    namespace: Option[String] = None,
                                    host: Option[String] = None,
                                    credentials: Option[Credentials]
                                  ): Datastore[F] = {
    val builder = DatastoreOptions.newBuilder()
      .setProjectId(projectId)
    namespace.foreach(ns => builder.setNamespace(ns))
    host.foreach(h => builder.setHost(h))
    credentials.foreach(c => builder.setCredentials(c))
    new DatastoreService(builder.build().getService)
  }

  def apply[F[_] : Sync : Functor](cloudDataStore: CloudDataStore): Datastore[F] = new DatastoreService(cloudDataStore)
}

class DatastoreService[F[_] : Sync : Functor](private val cloudDataStore: CloudDataStore) extends Datastore[F] with ReflectionHelper {

  private val keyFactories = collection.mutable.Map[String, KeyFactory]()

  override def newKey[E <: BaseEntity : TypeTag : ClassTag](): F[Key] = Sync[F].delay {
    val kind = getKind[E]()
    val incompleteKey = getKeyFactory(kind).newKey()
    Key(cloudDataStore.allocateId(incompleteKey))
  }

  override def newKey[E <: BaseEntity : TypeTag : ClassTag](name: String): F[Key] = Sync[F].delay {
    val kind = getKind[E]()
    val incompleteKey = getKeyFactory(kind).newKey(name)
    Key(cloudDataStore.allocateId(incompleteKey))
  }

  override def newKey[E <: BaseEntity : TypeTag : ClassTag](id: Long): F[Key] = Sync[F].delay {
    val kind = getKind[E]()
    val incompleteKey = getKeyFactory(kind).newKey(id)
    Key(cloudDataStore.allocateId(incompleteKey))
  }

  override def add[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[E] = Sync[F].delay {
    val clazz = extractRuntimeClass[E]()
    val kind = getKindByClass(clazz)
    val key = createKey(entity.id, kind)
    val dataStoreEntity = instanceToDatastoreEntity(key, entity, clazz)
    cloudDataStore.add(dataStoreEntity)
    entity
  }

  override def add[E <: BaseEntity : TypeTag : ClassTag](key: Key, entity: E): F[E] = Sync[F].delay {
    val clazz = extractRuntimeClass[E]()
    val datastoreEntity = instanceToDatastoreEntity(key, entity, clazz)
    val e = cloudDataStore.add(datastoreEntity)
    datastoreEntityToInstance[E](e, clazz)
  }

  override def add[E <: BaseEntity : TypeTag : ClassTag](ke: Map[Key, E]): F[List[E]] = Sync[F].delay {
    val clazz = extractRuntimeClass[E]()
    val entities = ke.map { case (k, v) => instanceToDatastoreEntity(k, v, clazz) }
    val es = cloudDataStore.add(entities.toArray: _*)
    es.asScala.toList.map(datastoreEntityToInstance[E](_, clazz))
  }

  override def get[E <: BaseEntity : TypeTag : ClassTag](id: Long): F[Option[E]] = Sync[F].delay {
    wrapGet[E](id)
  }

  override def get[E <: BaseEntity : TypeTag : ClassTag](id: String): F[Option[E]] = Sync[F].delay {
    wrapGet[E](id)
  }

  override def get[E <: BaseEntity : TypeTag : ClassTag](key: Key): F[Option[E]] = Sync[F].delay {
    wrapGet[E](key)
  }

  override def newTransaction: F[Transaction] = Sync[F].delay {
    cloudDataStore.newTransaction()
  }

  override def update[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[Unit] = {
    update[E](List(entity))
  }

  override def update[E <: BaseEntity : TypeTag : ClassTag](entities: List[E]): F[Unit] = Sync[F].delay {
    val es = convert[E](entities)
    cloudDataStore.update(es: _*)
  }

  override def put[E <: BaseEntity : TypeTag : ClassTag](entity: E): F[E] = {
    put(List(entity)).map(_ => entity)
  }

  override def put[E <: BaseEntity : TypeTag : ClassTag](entities: List[E]): F[List[E]] = Sync[F].delay {
    val es = convert[E](entities)
    cloudDataStore.put(es: _*)
    entities
  }

  override def delete[E <: BaseEntity : TypeTag : ClassTag](keys: Key*): F[Unit] = Sync[F].delay {
    cloudDataStore.delete(keys.map(_.key): _*)
  }

  override def delete[E <: BaseEntity : TypeTag : ClassTag](ids: List[Long]): F[Unit] = Sync[F].delay {
    val kind = getKind[E]()
    val kf = getKeyFactory(kind)
    val keys = ids.map(i => Key(kf.newKey(i)))
    delete(keys: _*)
  }

  override def getLazy[E <: BaseEntity : TypeTag : ClassTag, K](ids: List[K]): F[Iterator[E]] = Sync[F].delay {
    wrapLazyGet[E](ids)
  }

  override def fetch[E <: BaseEntity : TypeTag : ClassTag, K](ids: List[K]): F[List[Option[E]]] = Sync[F].delay {
    wrapFetch[E](ids)
  }

  private[datastore] def runQueryForSingleOpt[E <: BaseEntity : TypeTag : ClassTag](query: EntityQuery): F[Option[E]] = Sync[F].delay {
    val clazz = extractRuntimeClass[E]()
    val cloudDataStoreReader: DatastoreReader = cloudDataStore
    val results = cloudDataStoreReader.run(query)
    if (results.hasNext) {
      val entity = results.next()
      Some(datastoreEntityToInstance[E](entity, clazz))
    } else {
      None
    }
  }

  private[datastore] def runQueryForList[E <: BaseEntity : TypeTag : ClassTag](query: EntityQuery): F[List[E]] = Sync[F].delay {
    val clazz = extractRuntimeClass[E]()
    val cloudDataStoreReader: DatastoreReader = cloudDataStore
    val results = cloudDataStoreReader.run(query)

    @tailrec
    def iter(list: List[E]): List[E] = {
      if (results.hasNext) {
        iter(list :+ datastoreEntityToInstance[E](results.next, clazz))
      } else {
        list
      }
    }

    iter(List())
  }

  private def wrapFetch[E: TypeTag : ClassTag](ids: List[_]): List[Option[E]] = {
    val clazz = extractRuntimeClass[E]()
    val kind = getKindByClass(clazz)
    val es = ids.map(createKey(_, kind).key)
    val javaIterable: java.lang.Iterable[CloudKey] = es.asJava
    cloudDataStore
      .fetch(javaIterable)
      .asScala
      .map {
        Option(_).map(datastoreEntityToInstance[E](_, clazz))
      }
      .toList
  }

  private def wrapLazyGet[E: TypeTag : ClassTag](ids: List[_]): Iterator[E] = {
    val clazz = extractRuntimeClass[E]()
    val kind = getKindByClass(clazz)
    val es = ids.map(createKey(_, kind).key)
    val javaIterable: java.lang.Iterable[CloudKey] = es.asJava
    val scalaIterator = cloudDataStore
      .get(javaIterable)
      .asScala
      .map(datastoreEntityToInstance[E](_, clazz))
    scalaIterator
  }

  private def wrapGet[E: TypeTag : ClassTag](v: Any): Option[E] = {
    val clazz = extractRuntimeClass[E]()
    val kind = getKind[E]()
    val cloudDataStoreReader: DatastoreReader = cloudDataStore
    val key = v match {
      case id: Long => getKeyFactory(kind).newKey(id)
      case id: String => getKeyFactory(kind).newKey(id)
      case key: Key => key.key
    }
    val entity = Option(cloudDataStoreReader.get(key))
    entity.map(datastoreEntityToInstance[E](_, clazz))
  }

  private def getKeyFactory(kind: String) = {
    keyFactories.getOrElse(kind, {
      val keyFactory = cloudDataStore.newKeyFactory().setKind(kind)
      keyFactories.put(kind, keyFactory)
      keyFactory
    })
  }

  private[datastore] def getKind[E: ClassTag]() = {
    val clazz = extractRuntimeClass[E]()
    getKindByClass(clazz)
  }

  private[datastore] def getKindByClass(clazz: Class[_]): String = {
    Option(clazz.getDeclaredAnnotation(classOf[Kind])) match {
      case Some(customKeyAnnotation) if customKeyAnnotation.value() != null && customKeyAnnotation.value().nonEmpty =>
        customKeyAnnotation.value()
      case _ =>
        clazz.getCanonicalName
    }
  }

  private def createKey(id: Any, kind: String) = {
    val cloudKey = id match {
      case id: String => getKeyFactory(kind).newKey(id)
      case id: Long => getKeyFactory(kind).newKey(id)
      case id: Int => getKeyFactory(kind).newKey(id)
      case id: Key => id.key
      case otherId => throw UnsupportedIdTypeException(otherId.getClass.getCanonicalName)
    }
    Key(cloudKey)
  }

  private def convert[E <: BaseEntity : TypeTag : ClassTag](entities: Seq[E]): Seq[Entity] = {
    val clazz = extractRuntimeClass[E]()
    val kind = getKindByClass(clazz)
    entities map { e =>
      val key = createKey(e.id, kind)
      instanceToDatastoreEntity(key, e, clazz)
    }
  }

}
