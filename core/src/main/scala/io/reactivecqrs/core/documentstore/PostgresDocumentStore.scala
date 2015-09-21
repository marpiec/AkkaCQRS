package io.reactivecqrs.core.documentstore

import java.sql.ResultSet
import javax.sql.DataSource

import io.mpjsons.MPJsons
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.reflect.runtime.universe._

sealed trait PostgresDocumentStoreTrait[T <: AnyRef, M <: AnyRef] {

  implicit val t: TypeTag[T]
  implicit val m: TypeTag[M]

  val tableName: String
  val dbDataSource: DataSource
  val mpjsons: MPJsons

  if (!tableName.matches( """[a-zA-Z0-9\_]+""")) {
    throw new IllegalArgumentException("Invalid table name, only alphanumeric characters and underscore allowed")
  }

  protected final val projectionTableName = "projection_" + tableName

  protected val CREATE_TABLE_QUERY = s"CREATE TABLE IF NOT EXISTS $projectionTableName (" +
    "id BIGINT NOT NULL PRIMARY KEY, " +
    "document JSONB NOT NULL, metadata JSONB NOT NULL)"

  protected val UPDATE_DOCUMENT_QUERY = s"UPDATE $projectionTableName SET document = ?::jsonb, metadata = ?::jsonb WHERE id = ? "

  protected val SELECT_DOCUMENT_BY_ID_QUERY = s"SELECT document, metadata FROM $projectionTableName WHERE id = ?"

  protected def SELECT_DOCUMENT_BY_IDS_QUERY(ids: Seq[Long]) =
    s"SELECT id, document, metadata FROM $projectionTableName WHERE id IN ( ${ids.mkString(",")} )"

  protected val DELETE_DOCUMENT_BY_ID_QUERY = s"DELETE FROM $projectionTableName WHERE id = ?"

  protected def SELECT_DOCUMENT_BY_PATH(path: String) = s"SELECT id, document, metadata FROM $projectionTableName WHERE document #>> '{$path}' = ?"

  protected def SELECT_DOCUMENT_BY_PATH_WITH_ONE_OF_THE_VALUES(path: String, values: Set[String]) =
    s"SELECT id, document, metadata FROM $projectionTableName WHERE document #>> '{$path}' in (${values.map("'"+_+"'").mkString(",")})"

  protected val SELECT_ALL = s"SELECT id, document, metadata FROM $projectionTableName"

  init()

  protected def init(): Unit = {
    createTableIfNotExists()
  }

  protected def createTableIfNotExists(): Unit = {
    executeQuery(CREATE_TABLE_QUERY)
  }

  def executeQuery(query: String): Unit = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(query)
      try {
        statement.execute()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def updateDocument(key: Long, document: T, metadata: M): Unit = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(UPDATE_DOCUMENT_QUERY)
      try {
        statement.setString(1, mpjsons.serialize[T](document))
        statement.setString(2, mpjsons.serialize[M](metadata))
        statement.setLong(3, key)

        val numberOfUpdated = statement.executeUpdate()

        if (numberOfUpdated != 1) {
          throw new IllegalStateException("Expected 1, updated " + numberOfUpdated + " records")
        }

      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }


  def getDocument(key: Long): Option[DocumentWithMetadata[T, M]] = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(SELECT_DOCUMENT_BY_ID_QUERY)
      try {
        statement.setLong(1, key)
        val resultSet = statement.executeQuery()
        try {
          if (resultSet.next()) {
            Some(DocumentWithMetadata[T, M](mpjsons.deserialize[T](resultSet.getString(1)), mpjsons.deserialize[M](resultSet.getString(2))))
          } else {
            None
          }
        } finally {
          resultSet.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def removeDocument(key: Long): Unit = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(DELETE_DOCUMENT_BY_ID_QUERY)
      try {
        statement.setLong(1, key)
        statement.execute()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def findDocumentByPath(path: Seq[String], value: String): Map[Long, DocumentWithMetadata[T, M]] = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(SELECT_DOCUMENT_BY_PATH(path.mkString(",")))
      try {
        statement.setString(1, value)
        val resultSet = statement.executeQuery()
        try {
          val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
          while (resultSet.next()) {
            results += resultSet.getLong(1) -> DocumentWithMetadata[T,M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
          }
          results.toMap
        } finally {
          resultSet.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }


  def findDocumentsByPathWithOneOfTheValues(path: Seq[String], values: Set[String]): Map[Long, DocumentWithMetadata[T, M]] = {
    if (values.nonEmpty) {
      val connection = dbDataSource.getConnection
      try {
        val statement = connection.prepareStatement(SELECT_DOCUMENT_BY_PATH_WITH_ONE_OF_THE_VALUES(path.mkString(","), values))
        try {
          val resultSet = statement.executeQuery()
          try {
            val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
            while (resultSet.next()) {
              results += resultSet.getLong(1) -> DocumentWithMetadata[T, M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
            }
            results.toMap
          } finally {
            resultSet.close()
          }
        } finally {
          statement.close()
        }
      } finally {
        connection.close()
      }
    } else Map()
  }

  def findDocumentByPathWithOneArray[V](array: String, objectPath: Seq[String], value: V): Map[Long, DocumentWithMetadata[T, M]] = {
    findDocumentByPathWithOneArray("document", array, objectPath, value)
  }

  def findDocumentByPathWithOneArrayAnywhere[V](arrayPath: Seq[String], objectPath: Seq[String], value: V): Map[Long, DocumentWithMetadata[T, M]] = {
    findDocumentByPathWithOneArrayAnywhere("document", arrayPath, objectPath, value)
  }

  def findDocumentByMetadataPathWithOneArray[V](array: String, objectPath: Seq[String], value: V): Map[Long, DocumentWithMetadata[T, M]] = {
    findDocumentByPathWithOneArray("metadata", array, objectPath, value)
  }

  protected def findDocumentByPathWithOneArray[V](columnName: String, array: String, objectPath: Seq[String], value: V): Map[Long, DocumentWithMetadata[T, M]] = {
    val connection = dbDataSource.getConnection

    //sample query that works:
    // SELECT id, document FROM projection_process_info WHERE document -> 'setups' @> '[{"hasActiveInstance":true}]';
    def QUERY(array: String, path: String) =
      s"SELECT id, document, metadata FROM $projectionTableName WHERE $columnName -> '$array' @> '[$path]'"

    def makeJson(path: Seq[String], value: V): String =
      path match {
        case head :: tail => "{\"" + head + "\":" + makeJson(tail, value) + "}"
        case Nil => value match {
          case s: String => "\"" + s + "\""
          case anything => anything.toString
        }
      }

    try {
      val statement = connection.prepareStatement(QUERY(array, makeJson(objectPath, value)))
      try {
//        statement.setString(1, value)
        val resultSet = statement.executeQuery()
        try {
          val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
          while (resultSet.next()) {
            results += resultSet.getLong(1) -> DocumentWithMetadata[T,M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
          }
          results.toMap
        } finally {
          resultSet.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  protected def findDocumentByPathWithOneArrayAnywhere[V](columnName: String, array: Seq[String], objectPath: Seq[String], value: V): Map[Long, DocumentWithMetadata[T, M]] = {
    val connection = dbDataSource.getConnection

    //sample query that works:
    // SELECT * FROM projection_processes_flows WHERE document #> '{state, cursors}' @> '[{"currentNodeId":2}]';
    def QUERY(arrayPath: String, path: String) =
      s"SELECT id, document, metadata FROM $projectionTableName WHERE $columnName #> '$arrayPath' @> '[$path]'"

    def makeJson(path: Seq[String], value: V): String =
      path match {
        case head :: tail => "{\"" + head + "\":" + makeJson(tail, value) + "}"
        case Nil => value match {
          case s: String => "\"" + s + "\""
          case anything => anything.toString
        }
      }

    try {
      val statement = connection.prepareStatement(QUERY(array.mkString("{", ",", "}"), makeJson(objectPath, value)))
      try {
//        statement.setString(1, value)
        val resultSet = statement.executeQuery()
        try {
          val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
          while (resultSet.next()) {
            results += resultSet.getLong(1) -> DocumentWithMetadata[T,M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
          }
          results.toMap
        } finally {
          resultSet.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }


  def findAll(): Map[Long, DocumentWithMetadata[T, M]] = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(SELECT_ALL)
      try {
        val resultSet = statement.executeQuery()
        try {
          val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
          while (resultSet.next()) {
            results += resultSet.getLong(1) -> DocumentWithMetadata[T,M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
          }
          results.toMap
        } finally {
          resultSet.close()
        }
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

  def getDocuments(keys: List[Long]): Map[Long, DocumentWithMetadata[T, M]] = {
    if (keys.isEmpty) {
      Map[Long, DocumentWithMetadata[T, M]]()
    }
    else {
      val connection = dbDataSource.getConnection
      try {
        val statement = connection.prepareStatement(SELECT_DOCUMENT_BY_IDS_QUERY(keys))
        try {
          val resultSet: ResultSet = statement.executeQuery()
          try {
            val results = mutable.ListMap[Long, DocumentWithMetadata[T, M]]()
            while (resultSet.next()) {
              results += resultSet.getLong(1) -> DocumentWithMetadata[T,M](mpjsons.deserialize[T](resultSet.getString(2)), mpjsons.deserialize[M](resultSet.getString(3)))
            }
            results.toMap
          } finally {
            resultSet.close()
          }
        } finally {
          statement.close()
        }
      } finally {
        connection.close()
      }
    }
  }

}

class PostgresDocumentStore[T <: AnyRef, M <: AnyRef](val tableName: String, val dbDataSource: DataSource, val mpjsons: MPJsons)(implicit val t: TypeTag[T], val m: TypeTag[M])
  extends DocumentStore[T, M] with PostgresDocumentStoreTrait[T, M] {

  protected val INSERT_DOCUMENT_QUERY = s"INSERT INTO $projectionTableName (id, document, metadata) VALUES (?, ?::jsonb, ?::jsonb)"

  override def insertDocument(key: Long, document: T, metadata: M): Unit = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(INSERT_DOCUMENT_QUERY)
      try {
        statement.setLong(1, key)
        statement.setString(2, mpjsons.serialize(document))
        statement.setString(3, mpjsons.serialize(metadata))
        statement.execute()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }

}

class PostgresDocumentStoreAutoId[T <: AnyRef, M <: AnyRef](val tableName: String, val dbDataSource: DataSource, val mpjsons: MPJsons)(implicit val t: TypeTag[T], val m: TypeTag[M])
  extends DocumentStoreAutoId[T, M] with PostgresDocumentStoreTrait[T, M] {

  private final lazy val logger = LoggerFactory.getLogger(classOf[PostgresDocumentStoreAutoId[T, M]])

  protected final lazy val sequenceName = "sequence_" + tableName

  protected lazy val CREATE_SEQUENCE_QUERY = s"CREATE SEQUENCE $sequenceName START 1"

  protected lazy val INSERT_DOCUMENT_QUERY = s"INSERT INTO $projectionTableName (id, document, metadata) VALUES (nextval('$sequenceName'), ?::jsonb, ?::jsonb)"


  override protected def createTableIfNotExists(): Unit = {
    try {
      executeQuery(CREATE_SEQUENCE_QUERY)
    } catch {
      case e: Exception => () // IF NOT EXIST workaround
    }
    executeQuery(CREATE_TABLE_QUERY)
  }

  override def insertDocument(document: T, metadata: M): Unit = {
    val connection = dbDataSource.getConnection
    try {
      val statement = connection.prepareStatement(INSERT_DOCUMENT_QUERY)
      try {
        statement.setString(1, mpjsons.serialize(document))
        statement.setString(2, mpjsons.serialize(metadata))
        statement.execute()
      } finally {
        statement.close()
      }
    } finally {
      connection.close()
    }
  }
}
