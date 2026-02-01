package com.example.kotlinroomdatabase.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zeromq.ZContext
import org.zeromq.ZMQ

class ZmqSockets(private val serverAddress: String) {

    private val context = ZContext()
    private val socket: ZMQ.Socket = context.createSocket(ZMQ.REQ).apply {
        receiveTimeOut = 5000
        sendTimeOut = 3000
        linger = 0
        connect(serverAddress)
    }

    suspend fun sendData(data: String): String = withContext(Dispatchers.IO) {
        try {
            val sent = socket.send(data.toByteArray(ZMQ.CHARSET), 0)
            if (!sent) {
                Log.e("ZMQ", "Ошибка отправки данных (timeout/network)")
                return@withContext "ERROR_SEND"
            }
            val reply = socket.recv(0)

            if (reply == null) {
                Log.e("ZMQ", "Сервер не ответил (Timeout)")
                return@withContext "ERROR_TIMEOUT"
            }

            String(reply, ZMQ.CHARSET).also {
                Log.d("ZMQ", "Ответ: $it")
            }

        } catch (e: Exception) {
            Log.e("ZMQ", "Exception: ${e.message}")
            "ERROR_EXCEPTION"
        }
    }

    suspend fun testConnection(): String = sendData("0.0;0.0;0;0")

    fun close() {
        try {
            socket.close()
            context.close()
        } catch (e: Exception) {
            Log.e("ZMQ", "ошибка при закрытии сокета: ${e.message}")
        }
    }
}