package bot.afiliados

import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress
import javax.net.ssl.SSLSocketFactory

/** Certificado local para testar o cliente com HTTPS de verdade (o app recusa HTTP). */
object TesteTls {
    private val cert by lazy {
        HeldCertificate.Builder().addSubjectAlternativeName(InetAddress.getByName("localhost").canonicalHostName).addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
    }
    private val srv by lazy { HandshakeCertificates.Builder().heldCertificate(cert).build() }
    private val cli by lazy { HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build() }

    fun servidor(): SSLSocketFactory = srv.sslSocketFactory()
    fun clienteFactory(): SSLSocketFactory = cli.sslSocketFactory()
    fun clienteTrust() = cli.trustManager
}
