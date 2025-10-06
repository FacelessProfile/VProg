package com.UwU.students

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }

    }

    fun evaluateExpression(expr: String): Double {
        val expression = expr.replace(" ", "").replace(',', '.')
        val ops = setOf('+', '-', '*', '/')
        var opIndex = -1
        var opChar: Char? = null

        for (i in 1 until expression.length) {
            val c = expression[i]
            if (c in ops) {
                opIndex = i
                opChar = c
                break
            }
        }

        if (opIndex == -1) {
            return expression.toDoubleOrNull()
                ?: throw IllegalArgumentException("NAN: '$expression'")
        }

        val left = expression.substring(0, opIndex)
        val right = expression.substring(opIndex + 1)

        val a = left.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$left'")
        val b = right.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$right'")

        return when (opChar) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> {
                if (b == 0.0) throw ArithmeticException("Zero division error")
                a / b
            }

            else -> throw IllegalArgumentException("Unknown op")
        }
    }

    fun CalculateField(v: View) {
        val textField = v as TextView
        val expression = textField.text.toString()
        try {
            val result = evaluateExpression(expression)
            textField.text = if (result == result.toLong().toDouble()) {
                result.toLong().toString().replace('.',',')
            } else {
                result.toString().replace('.',',')
            }
        } catch (e: Exception) {
            textField.text = "Error!"
            Toast.makeText(this,"${e.message}",Toast.LENGTH_SHORT).show()
        }
    }

    fun TextChange(view: View) {
        val textField = findViewById<TextView>(R.id.Field)
        val button = view as Button
        if(textField.text=="Error!"){
            textField.text = "0"
        }
        if (button.text=="C"){
            textField.text = "0"
        }
        else if (textField.text =="0" && button.text=="0"){

        }
        else if (button.text=="="){
            CalculateField(textField)
        }
        else if (textField.text =="0" && button.text in "0123456789"){
            textField.text = button.text.toString()
        }
        else if(button.text in "+-*/,"){
            if (textField.text.last() !in "+-*/,") {
                textField.text = textField.text.toString() + button.text.toString()
            }
        }
        else{
            textField.text = textField.text.toString() + button.text.toString()
        }
    }

}