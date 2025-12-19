package com.example.kotlinroomdatabase.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zeromq.ZContext
import org.zeromq.ZMQ

class ZmqSockets(private val serverAddress: String) {

    private val context = ZContext()
    private val socket: ZMQ.Socket = context.createSocket(ZMQ.REQ).apply {
        setReceiveTimeOut(5000)
        connect(serverAddress)
    }

    suspend fun sendData(data: String): String = withContext(Dispatchers.IO) {
        try {
            val sent = socket.send(data.toByteArray(ZMQ.CHARSET), 0)
            if (!sent) {
                return@withContext """{"status":"error","message":"Send failed"}"""
            }

            val reply = socket.recv(0)
                ?: return@withContext """{"status":"error","message":"No response"}"""

            String(reply, ZMQ.CHARSET)

        } catch (e: Exception) {
            """{"status":"error","message":"${e.message}"}"""
        }
    }

    suspend fun testConnection(): String =
        sendData("""{"operation":"test"}""")

    fun close() {
        socket.close()
        context.close()
    }
}