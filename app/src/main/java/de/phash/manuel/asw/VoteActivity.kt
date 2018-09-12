package de.phash.manuel.asw

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import com.google.gson.Gson
import de.phash.manuel.asw.database.MyDatabaseOpenHelper
import de.phash.manuel.asw.database.database
import de.phash.manuel.asw.semux.APIService
import de.phash.manuel.asw.semux.APIService.Companion.unvote
import de.phash.manuel.asw.semux.APIService.Companion.vote
import de.phash.manuel.asw.semux.SemuxAddress
import de.phash.manuel.asw.semux.json.CheckBalance
import de.phash.manuel.asw.semux.key.*
import kotlinx.android.synthetic.main.activity_vote.*
import org.jetbrains.anko.db.classParser
import org.jetbrains.anko.db.parseList
import org.jetbrains.anko.db.select
import java.math.BigDecimal
import java.text.DecimalFormat

class VoteActivity : AppCompatActivity() {

    var locked = ""
    var address = ""
    var available = ""
    var nonce = ""
    val df = DecimalFormat("0.#########")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vote)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        address = intent.getStringExtra("address")
        locked = intent.getStringExtra("locked")
        available = intent.getStringExtra("available")
        checkAccount()
        val addressText = "${df.format(BigDecimal(available).divide(APIService.SEMUXMULTIPLICATOR))} SEM"
        voteAddressTextView.text = intent.getStringExtra("address")
        voteAvailableTextView.text = addressText
        val lockText = "${df.format(BigDecimal(locked).divide(APIService.SEMUXMULTIPLICATOR))} SEM"
        voteLockedTextView.text = lockText
    }

    fun onVoteTransactionClick(view: View) {

        if (voteReceivingAddressEditView.text.toString().isNotEmpty() && voteAmountEditView.text.toString().isNotEmpty()) {
            createTransaction(unvote)
        } else {
            if (voteReceivingAddressEditView.text.toString().isEmpty())
                Toast.makeText(this, "Receiver is empty", Toast.LENGTH_LONG).show()
            if (voteAmountEditView.text.toString().isEmpty())
                Toast.makeText(this, "Amount to vote is empty", Toast.LENGTH_LONG).show()
        }
    }

    fun onUnvoteTransactionClick(view: View) {

        if (voteReceivingAddressEditView.text.toString().isNotEmpty() && voteAmountEditView.text.toString().isNotEmpty()) {
            createTransaction(vote)
        } else {
            if (voteReceivingAddressEditView.text.toString().isEmpty())
                Toast.makeText(this, "Receiver is empty", Toast.LENGTH_LONG).show()
            if (voteAmountEditView.text.toString().isEmpty())
                Toast.makeText(this, "Amount to vote is empty", Toast.LENGTH_LONG).show()
        }
    }

    private fun createTransaction(option: String) {
        try {

            val receiver = Hex.decode0x(voteReceivingAddressEditView.text.toString())
            val semuxAddressList = getSemuxAddress(database)
            Log.i("PKEY", semuxAddressList.get(0).privateKey)
            val voterPkey = Key(Hex.decode0x(semuxAddressList.get(0).privateKey))
            val amount = Amount(voteAmountEditView.text.toString().toLong() * APIService.SEMUXMULTIPLICATOR.toLong())
            val fee = Amount(5000000)
            val type = if (option.equals(vote)) TransactionType.VOTE else TransactionType.UNVOTE
            var transaction = Transaction(APIService.NETWORK, type, receiver, amount, fee, nonce.toLong(), System.currentTimeMillis(), Bytes.EMPTY_BYTES)

            val signedTx = transaction.sign(voterPkey)

            voteTransaction(signedTx)
        } catch (e: Exception) {
            Log.e("SIGN", e.localizedMessage)
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_SHORT)
        }
    }


    private fun voteTransaction(transaction: Transaction) {
        var raw = Hex.encode0x(transaction.toBytes())
        Log.i("SEND", raw)
        val intent = Intent(this, APIService::class.java)
        // add infos for the service which file to download and where to store
        intent.putExtra(APIService.TRANSACTION_RAW, raw)
        intent.putExtra(APIService.TYP,
                APIService.transfer)
        startService(intent)
        Toast.makeText(this, "service started", Toast.LENGTH_SHORT)
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
        Toast.makeText(this, "service started", Toast.LENGTH_SHORT)

    }


    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            val bundle = intent.extras
            if (bundle != null) {
                when (bundle.getString(APIService.TYP)) {
                    APIService.check -> check(bundle)
                    APIService.vote -> vote(bundle)
                }

            }
        }

        private fun vote(bundle: Bundle) {
            val json = bundle.getString(APIService.JSON)
            val resultCode = bundle.getInt(APIService.RESULT)
            if (resultCode == Activity.RESULT_OK) {
                //  val account = Gson().fromJson(json, CheckBalance::class.java)
                Log.i("RES", json)
                Toast.makeText(this@VoteActivity,
                        "transfer done",
                        Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@VoteActivity, "transfer failed",
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
                Toast.makeText(this@VoteActivity,
                        "checked: ${account.message}",
                        Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@VoteActivity, "check failed",
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