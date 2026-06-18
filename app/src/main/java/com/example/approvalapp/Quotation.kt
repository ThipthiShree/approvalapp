package com.example.approvalapp

data class Quotation(

    val sno: Int,
    val reqno: String,
    val quoteowner: String,
    val accname: String,
    val opportunity: String,
    val payterm: String,
    val currency: String,
    val total: Double,
    val actionby: String,
    val actions: String,
    val buyingprice: Double = 0.0,
    val sellingprice: Double = 0.0,
    val marginrate: Double = 0.0,
    val items: List<QuotationItem> = emptyList()   // ← ADD THIS
)