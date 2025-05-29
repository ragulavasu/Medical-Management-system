package models

case class BillMedicine(
  medicineName: String,
  quantity: Int,
  unitPrice: Double
)

case class Bill(
  billId: String,
  medicines: List[BillMedicine],
  total: Double,
  date: String,          // Format: YYYY-MM-DD
  customerName: String
)
