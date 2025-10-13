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

    val ops = setOf('+', '-', '*', '/')

    fun findOperands(expr: String, index: Int): Pair<Int, Int> {
        var leftPointer = index - 1
        var rightPointer = index + 1

        while (leftPointer >= 0 && expr[leftPointer] !in ops) {
            leftPointer--
        }
        leftPointer++

        while (rightPointer < expr.length && expr[rightPointer] !in ops) {
            rightPointer++
        }

        return leftPointer to rightPointer
    }

    fun evaluateExpression(expr: String): Double {
        val expression = expr.replace(" ", "").replace(',', '.')
        var result = evaluateMultiplicationAndDivision(expression)
        result = evaluateAdditionAndSubtraction(result.toString())

        return result.toDouble()
    }

    fun evaluateMultiplicationAndDivision(expr: String): String {
        var expression = expr.replace(" ", "").replace(',', '.')
        var i = 0

        while (i < expression.length) {
            val c = expression[i]
            if (c == '*' || c == '/') {
                val (leftStart, rightEnd) = findOperands(expression, i)
                val leftStr = expression.substring(leftStart, i)
                val rightStr = expression.substring(i + 1, rightEnd)

                val a = leftStr.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$leftStr'")
                val b = rightStr.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$rightStr'")

                val result = when (c) {
                    '*' -> a * b
                    '/' -> {
                        if (b == 0.0) throw ArithmeticException("Zero division error")
                        a / b
                    }
                    else -> throw IllegalArgumentException("Unknown op")
                }
                val before = expression.substring(0, leftStart)
                val after = expression.substring(rightEnd)
                expression = before + result + after
                i = 0
            } else {
                i++
            }
        }

        return expression
    }

    fun evaluateAdditionAndSubtraction(expr: String): String {
        var expression = expr
        var i = 0

        while (i < expression.length) {
            val c = expression[i]
            if ((c == '+' || c == '-') && i > 0) {
                val (leftStart, rightEnd) = findOperands(expression, i)
                val leftStr = expression.substring(leftStart, i)
                val rightStr = expression.substring(i + 1, rightEnd)

                val a = leftStr.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$leftStr'")
                val b = rightStr.toDoubleOrNull() ?: throw IllegalArgumentException("NAN: '$rightStr'")

                val result = when (c) {
                    '+' -> a + b
                    '-' -> a - b
                    else -> throw IllegalArgumentException("Unknown op")
                }

                val before = expression.substring(0, leftStart)
                val after = expression.substring(rightEnd)
                expression = before + result + after
                i = 0
            } else {
                i++
            }
        }

        return expression
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