package com.example.approvalapp

data class SalesOrderItem(
    val sno: Int,
    val product: String,
    val qty: Int,
    val unitprice: Double,
    val totalprice: Double
)