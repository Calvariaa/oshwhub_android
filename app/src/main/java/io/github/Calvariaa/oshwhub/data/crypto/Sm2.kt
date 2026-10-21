package io.github.Calvariaa.oshwhub.data.crypto

import org.bouncycastle.crypto.engines.SM2Engine
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.params.ParametersWithRandom
import org.bouncycastle.asn1.gm.GMNamedCurves
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Base64

/**
 * 复刻 passport.jlc.com 的 `window.sm2Encrypt(data, publicKey, 1)`：
 *   msg = base64(utf8(data))            // 先 base64，再按 ASCII 当明文
 *   cipher = SM2(C1C3C2) over msg
 * 密文格式：04 || x1 || y1 || C3 || C2（小写 hex）
 */
object Sm2 {

    private const val CURVE = "sm2p256v1"

    fun encrypt(plain: String, publicKeyHex: String): String {
        val b64 = Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8))
        val message = b64.toByteArray(Charsets.UTF_8)

        var hex = publicKeyHex.trim()
        if (hex.startsWith("04") && hex.length >= 130) hex = hex.substring(2)
        require(hex.length >= 128) { "SM2 公钥格式不正确" }
        val x = BigInteger(hex.substring(0, 64), 16)
        val y = BigInteger(hex.substring(64, 128), 16)

        val x9 = GMNamedCurves.getByName(CURVE)
        val domain = ECDomainParameters(x9.curve, x9.g, x9.n, x9.h)
        val point = x9.curve.createPoint(x, y)
        val pub = ECPublicKeyParameters(point, domain)

        val engine = SM2Engine(SM2Engine.Mode.C1C3C2)
        engine.init(true, ParametersWithRandom(pub, SecureRandom()))
        val cipher = engine.processBlock(message, 0, message.size)
        return cipher.toHex()
    }

    private fun ByteArray.toHex(): String {
        val chars = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(chars[v ushr 4]).append(chars[v and 0x0F])
        }
        return sb.toString()
    }
}
