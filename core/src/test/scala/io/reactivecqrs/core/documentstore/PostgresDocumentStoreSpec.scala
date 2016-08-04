package io.reactivecqrs.core.documentstore

import io.mpjsons.MPJsons
import org.apache.commons.dbcp.BasicDataSource
import org.scalatest._
import scalikejdbc.DB

class PostgresDocumentStoreSpec extends FeatureSpec with GivenWhenThen with BeforeAndAfter with MustMatchers {

  val mpjsons = new MPJsons

  val testTable = "test_table"

  after {

  }

  feature("can find documents by path with one array") {
    scenario("value exists") {
      Given("document store with some values")
      val documentStore = new PostgresDocumentStore[ComplexType, NothingMetadata](testTable, mpjsons)
      DB.localTx { implicit connection =>

        documentStore.insertDocument(1, ComplexType(List(SimpleType(1), SimpleType(2))), NothingMetadata())
        documentStore.insertDocument(2, ComplexType(List(SimpleType(1))), NothingMetadata())
        documentStore.insertDocument(3, ComplexType(List(SimpleType(2), SimpleType(3))), NothingMetadata())

        When("document store is searched by array value")
        val result = documentStore.findDocumentByObjectInArray(List("simpleArray"), Seq("field"), 2)

        Then("correct documents are retrieved")
        result.keySet mustBe Set(1, 3)

        documentStore.dropTable()
      }
    }
  }

  feature("can find by option value") {
    scenario("option value exists") {
      Given("document store with one value")
      val documentStore = new PostgresDocumentStore[OptionIntType, NothingMetadata](testTable, mpjsons)
      DB.localTx { implicit connection =>
        documentStore.insertDocument(1, OptionIntType(Some(13)), NothingMetadata())

        When("searching by option value")
        val result = documentStore.findDocumentByPath(Seq("option", "value"), "13")

        Then("correct document is retrieved")
        result.keySet mustBe Set(1)

        documentStore.dropTable()
      }
    }
  }

  feature("i can has auto id") {
    scenario("inserting document and getting auto id back") {
      Given("empty document store")
      val documentStore = new PostgresDocumentStoreAutoId[SimpleType, NothingMetadata](testTable, mpjsons)

      DB.localTx { implicit connection =>
        When("document is inserted")
        val result = documentStore.insertDocument(SimpleType(42), NothingMetadata())

        Then("id must be 1")
        result mustBe 1

        When("another document is inserted")
        val another = documentStore.insertDocument(SimpleType(44), NothingMetadata())

        Then("another id must be 2")
        another mustBe 2

        documentStore.dropTable()
      }
    }
  }

}
