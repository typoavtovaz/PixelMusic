package com.unshoo.pixelmusic

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object DpiBypass {
    // 1. Дробление TCP-буфера, чтобы разбивать пакет ClientHello (SNI) для обхода ТСПУ
    private val splitSocketFactory = object : SocketFactory() {
        private val delegate = getDefault()

        private fun Socket.patch(): Socket = apply {
            tcpNoDelay = true
            sendBufferSize = 64 // Режем буфер отправки на мелкие куски
        }

        override fun createSocket(): Socket = delegate.createSocket().patch()
        override fun createSocket(host: String, port: Int): Socket = delegate.createSocket(host, port).patch()
        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            delegate.createSocket(host, port, localHost, localPort).patch()
        override fun createSocket(host: InetAddress, port: Int): Socket = delegate.createSocket(host, port).patch()
        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            delegate.createSocket(address, port, localAddress, localPort).patch()
    }

    // 2. Чанкинг потока googlevideo, чтобы сервера не зависали при воспроизведении
    private val rangeInterceptor = Interceptor { chain ->
        val req = chain.request()
        val host = req.url.host
        if (host.contains("googlevideo.com") && req.header("Range") == null) {
            val newReq = req.newBuilder()
                .header("Range", "bytes=0-10485759") // Тянем кусками по 10 МБ
                .build()
            chain.proceed(newReq)
        } else {
            chain.proceed(req)
        }
    }

    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder
            .socketFactory(splitSocketFactory)
            .addInterceptor(rangeInterceptor)
    }

    fun createClient(): OkHttpClient {
        return applyTo(OkHttpClient.Builder()).build()
    }
}
