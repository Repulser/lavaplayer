package com.sedmelluq.discord.lavaplayer.tools.io

import spock.lang.Specification

import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PersistentHttpStreamTest extends Specification {
  def "closing a stream closes its server-side connection"() {
    given:
    def server = new ServerSocket(0)
    def executor = Executors.newSingleThreadExecutor()
    def peerObservedClose = executor.submit({
      server.accept().withCloseable { socket ->
        socket.soTimeout = 3000
        def reader = socket.inputStream.newReader()
        while (reader.readLine()) {
          // Consume request headers.
        }
        socket.outputStream.write(
            "HTTP/1.1 200 OK\r\nContent-Length: 4\r\nConnection: keep-alive\r\n\r\ndata".bytes
        )
        socket.outputStream.flush()
        try {
          return socket.inputStream.read() == -1
        } catch (SocketException ignored) {
          // A forced close may surface as RST rather than EOF on the peer.
          return true
        }
      }
    } as Callable<Boolean>)
    def manager = new SimpleHttpInterfaceManager(
        HttpClientTools.createSharedCookiesHttpBuilder(),
        HttpClientTools.DEFAULT_REQUEST_CONFIG
    )
    def httpInterface = manager.getInterface()
    def stream = new PersistentHttpStream(
        httpInterface,
        new URI("http://127.0.0.1:${server.localPort}/audio"),
        null
    )

    when:
    stream.checkStatusCode()
    stream.close()
    stream.close()

    then:
    peerObservedClose.get(5, TimeUnit.SECONDS)

    cleanup:
    httpInterface?.close()
    manager?.close()
    server?.close()
    executor?.shutdownNow()
  }
}
