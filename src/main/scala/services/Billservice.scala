package services

import models.Bill
import models.BillMedicine
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

object BillService {
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  // Custom codec registry for Bill case class
  val codecRegistry = fromRegistries(
    fromProviders(classOf[Bill]),
    DEFAULT_CODEC_REGISTRY
  )
  
  val database = MongoConnection.database
  val collection: MongoCollection[Bill] = database
    .getCollection[Bill]("bills")
    .withCodecRegistry(codecRegistry)

  def addBill(bill: Bill): Future[Unit] = {
    collection.insertOne(bill).toFuture().map { _ =>
      println("Bill added.")
    }.recover {
      case ex => println(s"Error adding bill: ${ex.getMessage}")
    }
  }

  def getAllBills(): Future[Seq[Bill]] = {
    // Filter: exclude documents with init: true or missing/empty billId
    collection.find(
      and(
        or(
          exists("init", false),
          equal("init", false)
        ),
        exists("billId", true),
        not(equal("billId", ""))
      )
    ).toFuture()
  }

  def searchBills(id: String): Future[Seq[Bill]] = {
    collection.find(equal("billId", id)).toFuture()
  }

  def searchBillsSync(id: String): Unit = {
    val future = searchBills(id)
    future.onComplete {
      case Success(results) =>
        if (results.nonEmpty) results.foreach(println)
        else println("Bill not found.")
      case Failure(ex) =>
        println(s"Error searching bills: ${ex.getMessage}")
    }
  }

  def getBillsByMedicine(medicineName: String): Future[Seq[Bill]] = {
    collection.find(equal("medicineName", medicineName)).toFuture()
  }

  def getBillsByDateRange(startDate: String, endDate: String): Future[Seq[Bill]] = {
    collection.find(
      and(
        gte("date", startDate),
        lte("date", endDate)
      )
    ).toFuture()
  }
}