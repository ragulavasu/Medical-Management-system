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
import scala.jdk.CollectionConverters._

object BillService {
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  // Custom codec registry for Bill and BillMedicine case classes
  val codecRegistry = fromRegistries(
    fromProviders(classOf[Bill], classOf[BillMedicine]),
    DEFAULT_CODEC_REGISTRY
  )
  
  val database = MongoConnection.database
  // Change collection type to Document to handle raw BSON
  val collection: MongoCollection[Document] = database
    .getCollection[Document]("bills")
    .withCodecRegistry(codecRegistry)

  def addBill(bill: Bill): Future[Unit] = {
    // Create a document that explicitly includes all fields
    val billDocument = Document(
      "billId" -> bill.billId,
      "medicines" -> bill.medicines.map(med => Document(
        "medicineName" -> med.medicineName,
        "quantity" -> med.quantity,
        "unitPrice" -> med.unitPrice
      )),
      "total" -> bill.total,
      "date" -> bill.date,
      "customerName" -> bill.customerName
    )

    collection.insertOne(billDocument).toFuture().map { _ =>
      println(s"Bill added with ID: ${bill.billId}")
    }.recover {
      case ex => 
        println(s"Error adding bill: ${ex.getMessage}")
        throw ex // Re-throw to handle in the route
    }
  }

  private def documentToBill(doc: Document): Bill = {
    Bill(
      billId = doc.getString("billId"),
      medicines = doc.getList("medicines", classOf[Document]).asScala.map { medDoc =>
        BillMedicine(
          medicineName = medDoc.getString("medicineName"),
          quantity = medDoc.getInteger("quantity"),
          unitPrice = medDoc.getDouble("unitPrice")
        )
      }.toList,
      total = doc.getDouble("total"),
      date = doc.getString("date"),
      customerName = doc.getString("customerName")
    )
  }

  def getAllBills(): Future[Seq[Bill]] = {
    collection.find(
      and(
        exists("billId", true),
        not(equal("billId", "")),
        exists("medicines", true)
      )
    ).toFuture().map { docs =>
      docs.map(documentToBill)
    }
  }

  def searchBills(id: String): Future[Seq[Bill]] = {
    collection.find(equal("billId", id)).toFuture().map { docs =>
      docs.map(documentToBill)
    }
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
    collection.find(
      elemMatch("medicines", equal("medicineName", medicineName))
    ).toFuture().map { docs =>
      docs.map(documentToBill)
    }
  }

  def getBillsByDateRange(startDate: String, endDate: String): Future[Seq[Bill]] = {
    collection.find(
      and(
        gte("date", startDate),
        lte("date", endDate)
      )
    ).toFuture().map { docs =>
      docs.map(documentToBill)
    }
  }
}