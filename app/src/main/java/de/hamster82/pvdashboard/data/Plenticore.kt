package de.hamster82.pvdashboard.data

import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import org.json.JSONArray
import org.json.JSONObject

class Live(
    val pvKw: Double?, val hausKw: Double?, val akkuSoc: Double?, val akkuKw: Double?, val netzKw: Double?,
    val tagErtrag: Double?, val tagHaus: Double?, val tagPv: Double?, val tagBat: Double?, val tagAutarkie: Double?,
    val monatErtrag: Double?, val monatHaus: Double?, val monatAutarkie: Double?,
    val jahrErtrag: Double?, val jahrHaus: Double?, val jahrAutarkie: Double?,
)

class PlenticoreResult(val live: Live, val pin: String, val adresse: String)

class PlenticoreException(msg: String, val endgueltig: Boolean = false) : Exception(msg)

/** SCRAM-SHA256-Anmeldung wie die Weboberfläche des Kostal Plenticore (Benutzer "Anlagenbetreiber"). */
object Scram {
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { m.update(it) }
        return m.doFinal()
    }

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    class Keys(val proof: String, val serverSignature: ByteArray, val protocolKey: ByteArray)

    fun derive(password: String, clientNonce: String, serverNonce: String, saltB64: String, rounds: Int): Keys {
        val salted = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), b64d.decode(saltB64), rounds, 256)).encoded
        val clientKey = hmac(salted, "Client Key".toByteArray())
        val storedKey = sha256(clientKey)
        val authMsg = "n=user,r=$clientNonce,r=$serverNonce,s=$saltB64,i=$rounds,c=biws,r=$serverNonce".toByteArray()
        val clientSig = hmac(storedKey, authMsg)
        val proof = ByteArray(clientKey.size) { (clientKey[it].toInt() xor clientSig[it].toInt()).toByte() }
        val serverSig = hmac(hmac(salted, "Server Key".toByteArray()), authMsg)
        val protocolKey = hmac(storedKey, "Session Key".toByteArray(), authMsg, clientKey)
        return Keys(b64e.encodeToString(proof), serverSig, protocolKey)
    }

    /** AES-256-GCM: liefert (Ciphertext, Tag) */
    fun encrypt(key: ByteArray, iv: ByteArray, plain: ByteArray): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val out = c.doFinal(plain)
        return out.copyOfRange(0, out.size - 16) to out.copyOfRange(out.size - 16, out.size)
    }

    fun b64(b: ByteArray): String = b64e.encodeToString(b)
    fun unb64(s: String): ByteArray = b64d.decode(s)
}

object Plenticore {
    /** Versucht zuerst die Adresse im Heimnetz, dann die Adresse für unterwegs. Nur lesend. */
    fun read(s: Settings): PlenticoreResult {
        if (s.plenticorePasswort.isBlank()) throw PlenticoreException("Kein Passwort eingetragen", true)
        val adressen = listOf(s.plenticoreLokal, s.plenticoreExtern).map { it.trim() }.filter { it.isNotEmpty() }
        if (adressen.isEmpty()) throw PlenticoreException("Keine Adresse eingetragen", true)
        var letzter: Exception? = null
        for ((i, a) in adressen.withIndex()) {
            try {
                val timeout = if (i == 0 && adressen.size > 1) 2500 else 6000
                return readFrom(a, s.plenticorePasswort, s.zertifikatPin, timeout)
            } catch (e: PlenticoreException) {
                if (e.endgueltig) throw e
                letzter = e
            } catch (e: Exception) {
                letzter = e
            }
        }
        throw PlenticoreException(letzter?.kurz() ?: "nicht erreichbar")
    }

    private fun hostPort(a: String): Pair<String, Int> {
        val clean = a.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val idx = clean.lastIndexOf(':')
        return if (idx > 0 && clean.indexOf(':') == idx) clean.substring(0, idx) to (clean.substring(idx + 1).toIntOrNull() ?: 443)
        else clean to 443
    }

    private class Conn(val host: String, val port: Int, val pin: String, val timeout: Int) {
        var gesehenerPin = ""
        var pinFalsch = false
        private val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) {
                val fp = Scram.sha256(chain[0].encoded).joinToString(":") { "%02X".format(it) }
                gesehenerPin = fp
                if (pin.isNotBlank() && normPin(pin) != normPin(fp)) {
                    pinFalsch = true
                    throw CertificateException("Zertifikat passt nicht")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        private val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }

        fun call(path: String, method: String = "GET", body: Any? = null, session: String? = null): String {
            val c = URL("https", host, port, "/api/v1$path").openConnection() as HttpsURLConnection
            c.sslSocketFactory = ssl.socketFactory
            c.hostnameVerifier = HostnameVerifier { _, _ -> true } // Absicherung erfolgt über den Zertifikats-Fingerabdruck
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.requestMethod = method
            c.setRequestProperty("Accept", "application/json")
            if (session != null) c.setRequestProperty("Authorization", "Session $session")
            try {
                if (body != null) {
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    if (path.startsWith("/auth/finish")) throw PlenticoreException("Passwort falsch (Anlagenbetreiber)", true)
                    throw PlenticoreException("HTTP $code bei ${path.substringBefore('?')}")
                }
                return text
            } catch (e: Exception) {
                if (pinFalsch) throw PlenticoreException("Zertifikat des Wechselrichters hat sich geändert – in den Einstellungen zurücksetzen, falls das gewollt ist", true)
                throw e
            } finally {
                c.disconnect()
            }
        }
    }

    fun normPin(p: String) = p.replace(":", "").replace(" ", "").lowercase()

    private fun readFrom(adresse: String, passwort: String, pin: String, timeout: Int): PlenticoreResult {
        val (host, port) = hostPort(adresse)
        val conn = Conn(host, port, pin, timeout)
        val rnd = SecureRandom()

        val clientNonce = Scram.b64(ByteArray(12).also { rnd.nextBytes(it) })
        val start = JSONObject(conn.call("/auth/start", "POST", JSONObject().put("username", "user").put("nonce", clientNonce)))
        val keys = Scram.derive(passwort, clientNonce, start.getString("nonce"), start.getString("salt"), start.getInt("rounds"))
        val tid = start.getString("transactionId")
        val fin = JSONObject(conn.call("/auth/finish", "POST", JSONObject().put("transactionId", tid).put("proof", keys.proof)))
        if (!Scram.unb64(fin.getString("signature")).contentEquals(keys.serverSignature)) {
            throw PlenticoreException("Antwort des Wechselrichters ist nicht vertrauenswürdig", true)
        }
        val iv = ByteArray(16).also { rnd.nextBytes(it) }
        val (ct, tag) = Scram.encrypt(keys.protocolKey, iv, fin.getString("token").toByteArray())
        val sess = JSONObject(conn.call("/auth/create_session", "POST", JSONObject()
            .put("transactionId", tid).put("iv", Scram.b64(iv)).put("tag", Scram.b64(tag)).put("payload", Scram.b64(ct))))
        val sid = sess.getString("sessionId")

        val werte = HashMap<String, Double>()
        try {
            for (m in listOf("devices:local", "devices:local:battery", "devices:local:pv1", "devices:local:pv2", "scb:statistic:EnergyFlow")) {
                runCatching {
                    val arr = JSONArray(conn.call("/processdata/$m", session = sid))
                    for (i in 0 until arr.length()) {
                        val mod = arr.getJSONObject(i)
                        val pd = mod.optJSONArray("processdata") ?: continue
                        for (k in 0 until pd.length()) {
                            val p = pd.getJSONObject(k)
                            val v = p.optDouble("value", Double.NaN)
                            if (!v.isNaN()) werte["${mod.getString("moduleid")}/${p.getString("id")}"] = v
                        }
                    }
                }
            }
        } finally {
            runCatching { conn.call("/auth/logout", "POST", JSONObject(), sid) }
        }
        if (werte.isEmpty()) throw PlenticoreException("Keine Daten vom Wechselrichter")
        return PlenticoreResult(parse(werte), conn.gesehenerPin, adresse)
    }

    fun parse(w: Map<String, Double>): Live {
        fun kw(k: String) = w[k]?.div(1000.0)
        fun kwh(k: String) = w["scb:statistic:EnergyFlow/Statistic:$k"]?.div(1000.0)
        fun pct(k: String) = w["scb:statistic:EnergyFlow/Statistic:$k"]
        val pv1 = w["devices:local:pv1/P"]
        val pv2 = w["devices:local:pv2/P"]
        return Live(
            pvKw = if (pv1 == null && pv2 == null) null else ((pv1 ?: 0.0) + (pv2 ?: 0.0)) / 1000.0,
            hausKw = kw("devices:local/Home_P"),
            akkuSoc = w["devices:local:battery/SoC"],
            akkuKw = kw("devices:local:battery/P"),
            netzKw = kw("devices:local/Grid_P"),
            tagErtrag = kwh("Yield:Day"), tagHaus = kwh("EnergyHome:Day"),
            tagPv = kwh("EnergyHomePv:Day"), tagBat = kwh("EnergyHomeBat:Day"), tagAutarkie = pct("Autarky:Day"),
            monatErtrag = kwh("Yield:Month"), monatHaus = kwh("EnergyHome:Month"), monatAutarkie = pct("Autarky:Month"),
            jahrErtrag = kwh("Yield:Year"), jahrHaus = kwh("EnergyHome:Year"), jahrAutarkie = pct("Autarky:Year"),
        )
    }
}
