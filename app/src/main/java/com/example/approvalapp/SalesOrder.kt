package com.example.approvalapp

data class SalesOrder(
    val sno: Int,
    val requestno: String,
    val quoteowner: String,
    val quotename: String,
    val paymentterms: String,
    val total: Double,
    val assignedto: String,
    val actionby: String,
    val actions: String,
    val buyingprice: Double = 0.0,       // ← ADD
    val sellingprice: Double = 0.0,      // ← ADD
    val marginrate: Double = 0.0,        // ← ADD
    val items: List<SalesOrderItem> = emptyList()  // ← ADD
)