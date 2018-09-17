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

package de.phash.manuel.asw

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import de.phash.manuel.asw.database.MyDatabaseOpenHelper
import de.phash.manuel.asw.database.database
import de.phash.manuel.asw.semux.APIService
import de.phash.manuel.asw.semux.APIService.Companion.FEE
import de.phash.manuel.asw.semux.SemuxAddress
import de.phash.manuel.asw.semux.json.CheckBalance
import de.phash.manuel.asw.semux.json.transactionraw.RawTransaction
import de.phash.manuel.asw.semux.key.*
import de.phash.manuel.asw.util.DeCryptor
import de.phash.manuel.asw.util.isPasswordCorrect
import de.phash.manuel.asw.util.isPasswordSet
import kotlinx.android.synthetic.main.activity_send.*
import kotlinx.android.synthetic.main.password_prompt.view.*
import org.jetbrains.anko.db.classParser
import org.jetbrains.anko.db.parseList
import org.jetbrains.anko.db.select
import java.math.BigDecimal


class SendActivity : AppCompatActivity() {
    var locked = ""
    var address = ""
    var available = ""
    var nonce = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        address = intent.getStringExtra("address")
        locked = intent.getStringExtra("locked")
        available = intent.getStringExtra("available")
        checkAccount()
        val addressText = "${APIService.SEMUXFORMAT.format(BigDecimal(available).divide(APIService.SEMUXMULTIPLICATOR))} SEM"
        sendAddressTextView.text = intent.getStringExtra("address")
        sendAvailableTextView.text = addressText
        val lockText = "${APIService.SEMUXFORMAT.format(BigDecimal(locked).divide(APIService.SEMUXMULTIPLICATOR))} SEM"
        sendLockedTextView.text = lockText

    }

    fun onSendTransactionClick(view: View) {

        if (sendReceivingAddressEditView.text.toString().isNotEmpty() && sendAmountEditView.text.toString().isNotEmpty()) {
            if (isPasswordSet(this)) {
                passwordSecured()
            } else {
                createTransaction()
            }
        } else {
            if (sendReceivingAddressEditView.text.toString().isEmpty())
                Toast.makeText(this, "Receiver is empty", Toast.LENGTH_LONG).show()
            if (sendAmountEditView.text.toString().isEmpty())
                Toast.makeText(this, "Amount to send is empty", Toast.LENGTH_LONG).show()
        }

    }

    fun passwordSecured() {
        val dialogBuilder = AlertDialog.Builder(this)
        val inflater = LayoutInflater.from(this)
        val promptView = inflater.inflate(R.layout.password_prompt, null)
        dialogBuilder.setView(promptView)

        dialogBuilder.setCancelable(true).setOnCancelListener(DialogInterface.OnCancelListener { dialog ->
            dialog.dismiss()
        })
                .setPositiveButton("SEND") { dialog, which ->
                    Log.i("PASSWORD", "positive button")
                    if (promptView.enterOldPassword.text.toString().isEmpty()) {
                        Toast.makeText(this, "Input does not match your current password", Toast.LENGTH_LONG).show()
                    } else {
                        if (isPasswordCorrect(this, promptView.enterOldPassword.text.toString())) {
                            createTransaction()

                        } else {
                            Log.i("PASSWORD", "PW false")
                            Toast.makeText(this, "Input does not match your current password", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("CANCEL") { dialog, which ->
                    Log.i("PASSWORD", "negative button")
                    dialog.dismiss()
                }
        val dialog: AlertDialog = dialogBuilder.create()
        dialog.show()
    }

    private var mFirebaseAnalytics: FirebaseAnalytics? = null
    private fun createTransaction() {
        try {
            val bundle = Bundle()

            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "5")
            bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "send")

            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)
            mFirebaseAnalytics!!.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle)
            val receiver = Hex.decode0x(sendReceivingAddressEditView.text.toString())
            val semuxAddressList = getSemuxAddress(database)
            val account = semuxAddressList.get(0)
            val decryptedKey = DeCryptor().decryptData(account.address + "s", Hex.decode0x(account.privateKey), Hex.decode0x(account.ivs))

            val senderPkey = Key(Hex.decode0x(decryptedKey))
            val amount = Amount.Unit.SEM.of(sendAmountEditView.text.toString().toLong())

            var transaction = Transaction(APIService.NETWORK, TransactionType.TRANSFER, receiver, amount, FEE, nonce.toLong(), System.currentTimeMillis(), Bytes.EMPTY_BYTES)
            val signedTx = transaction.sign(senderPkey)

            sendTransaction(signedTx)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("SIGN", e.localizedMessage ?: "NIX")
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT)
        }
    }


    private fun sendTransaction(transaction: Transaction) {
        var raw = Hex.encode0x(transaction.toBytes())
        Log.i("SEND", raw)
        val intent = Intent(this, APIService::class.java)
        // add infos for the service which file to download and where to store
        intent.putExtra(APIService.TRANSACTION_RAW, raw)
        intent.putExtra(APIService.TYP,
                APIService.transfer)
        startService(intent)

    }


    fun getSemuxAddress(db: MyDatabaseOpenHelper): List<SemuxAddress> = db.use {
        Log.i("PKEY", "address: ${address}")
        select(MyDatabaseOpenHelper.SEMUXADDRESS_TABLENAME)
                .whereArgs("${SemuxAddress.COLUMN_ADDRESS} = {address}", "address" to address.substring(2))
                .exec { parseList(classParser<SemuxAddress>()) }

    }


    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, IntentFilter(
                APIService.NOTIFICATION_TRANSFER))
        registerReceiver(receiver, IntentFilter(
                APIService.NOTIFICATION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    fun checkAccount() {

        val intent = Intent(this, APIService::class.java)
        // add infos for the service which file to download and where to store
        intent.putExtra(APIService.ADDRESS, address)
        intent.putExtra(APIService.TYP,
                APIService.check)
        startService(intent)
    }


    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val bundle = intent.extras
            if (bundle != null) {
                when (bundle.getString(APIService.TYP)) {
                    APIService.check -> check(bundle)
                    APIService.transfer -> transfer(bundle)
                }
            }
        }

        private fun transfer(bundle: Bundle) {
            val json = bundle.getString(APIService.JSON)
            val resultCode = bundle.getInt(APIService.RESULT)
            if (resultCode == Activity.RESULT_OK) {
                val tx = Gson().fromJson(json, RawTransaction::class.java)
                Log.i("RES", json)
                if (tx.success)
                    Toast.makeText(this@SendActivity,
                            "transfer done",
                            Toast.LENGTH_LONG).show()
                else {
                    Toast.makeText(this@SendActivity,
                            tx.message,
                            Toast.LENGTH_LONG).show()

                }
            } else {
                Toast.makeText(this@SendActivity, "transfer failed",
                        Toast.LENGTH_LONG).show()

            }
        }

        private fun check(bundle: Bundle) {

            val json = bundle.getString(APIService.JSON)
            val resultCode = bundle.getInt(APIService.RESULT)
            if (resultCode == Activity.RESULT_OK) {
                val account = Gson().fromJson(json, CheckBalance::class.java)
                Log.i("RES", json)
                nonce = account.result.nonce

            } else {
                Toast.makeText(this@SendActivity, "check failed",
                        Toast.LENGTH_LONG).show()

            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        startNewActivity(item, this)
        return super.onOptionsItemSelected(item)
    }
}
