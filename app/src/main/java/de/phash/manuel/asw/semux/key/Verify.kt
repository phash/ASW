/*
 * MIT License
 *
 * Copyright (c) 2018 Manuel Roedig / Phash
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.phash.manuel.asw.semux.key

import android.util.Log
import net.i2p.crypto.eddsa.EdDSAEngine

/**
 * Verifies a signature.
 *
 * @param message
 * message
 * @param signature
 * signature
 * @return True if the signature is valid, otherwise false
 */
fun verify(message: ByteArray, signature: Key.Signature?): Boolean {
    if (message != null && signature != null) { // avoid null pointer exception
        try {

            val engine = EdDSAEngine()
            engine.initVerify(PublicKeyCache.computeIfAbsent(signature.publicKey))

            return engine.verifyOneShot(message, signature.s)

        } catch (e: Exception) {
            Log.i("VERIFY", e.localizedMessage)
        }

    }

    return false
}