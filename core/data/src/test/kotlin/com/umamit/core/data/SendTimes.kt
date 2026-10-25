package com.umamit.core.data

import java.io.FilterOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Plain sockets that note in [sent] the moment each HTTP request starts going onto the wire: the
 * first write of a request line. OkHttp creates its sockets unconnected with [createSocket].
 */
internal class SendTimes(private val sent: MutableList<Long>) : SocketFactory() {
    override fun createSocket(): Socket = object : Socket() {
        private val output by lazy { RequestStarts(super.getOutputStream(), sent) }

        override fun getOutputStream(): OutputStream = output
    }

    override fun createSocket(host: String?, port: Int): Socket = unsupported()

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = unsupported()

    override fun createSocket(host: InetAddress?, port: Int): Socket = unsupported()

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("OkHttp uses createSocket()")
}

/** Notes the time of every write that starts a request line (`GET `) before passing it on. */
private class RequestStarts(out: OutputStream, private val sent: MutableList<Long>) : FilterOutputStream(out) {
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len >= REQUEST_START.size && (REQUEST_START.indices).all { b[off + it] == REQUEST_START[it] }) {
            sent += System.nanoTime()
        }
        out.write(b, off, len)
    }

    private companion object {
        val REQUEST_START = "GET ".toByteArray()
    }
}
