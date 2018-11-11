package com.mkring.james.chatbackend.rocketchat

import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.Provider
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * ssl context ignoring cert paths
 */
internal object NaiveSSLContext {

    /**
     * Get an SSLContext that implements the specified secure
     * socket protocol and naively accepts all certificates
     * without verification.
     */
    @Throws(NoSuchAlgorithmException::class)
    fun getInstance(protocol: String): SSLContext {
        return init(SSLContext.getInstance(protocol))
    }

    /**
     * Get an SSLContext that implements the specified secure
     * socket protocol and naively accepts all certificates
     * without verification.
     */
    @Throws(NoSuchAlgorithmException::class)
    fun getInstance(protocol: String, provider: Provider): SSLContext {
        return init(SSLContext.getInstance(protocol, provider))
    }

    /**
     * Get an SSLContext that implements the specified secure
     * socket protocol and naively accepts all certificates
     * without verification.
     */
    @Throws(NoSuchAlgorithmException::class, NoSuchProviderException::class)
    fun getInstance(protocol: String, provider: String): SSLContext {
        return init(SSLContext.getInstance(protocol, provider))
    }

    /**
     * Set NaiveTrustManager to the given context.
     */
    private fun init(context: SSLContext): SSLContext {
        try {
            // Set NaiveTrustManager.
            context.init(null, arrayOf<TrustManager>(NaiveTrustManager), null)
        } catch (e: KeyManagementException) {
            throw RuntimeException("Failed to initialize an SSLContext.", e)
        }

        return context
    }

    internal object NaiveTrustManager : X509TrustManager {
        override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
        }

        override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> {
            return emptyArray()
        }
    }
}