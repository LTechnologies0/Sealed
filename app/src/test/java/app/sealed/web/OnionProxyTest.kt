package app.sealed.web

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class OnionProxyTest {
    @Test
    fun socksTargetsOnionVpnLoopback() {
        assertEquals("127.0.0.1", OnionProxy.HOST)
        assertEquals(18202, OnionProxy.PORT)
        val proxy = OnionProxy.socks()
        assertEquals(Proxy.Type.SOCKS, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(18202, address.port)
    }
}
