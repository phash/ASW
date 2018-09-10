package de.phash.manuel.asw

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import de.phash.manuel.asw.semux.APIService
import kotlinx.android.synthetic.main.activity_single_balance.*
import java.math.BigDecimal
import java.text.DecimalFormat

class SingleBalanceActivity : AppCompatActivity() {
    val df = DecimalFormat("0.#########")
    var locked = ""
    var address = ""
    var available = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_single_balance)
        address = intent.getStringExtra("address")
        available = intent.getStringExtra("available")
        locked = intent.getStringExtra("locked")

        singleBalanceAddress.text = address
        singleBalanceAvailable.text = df.format(BigDecimal(available).divide(APIService.SEMUXMULTIPLICATOR))
        singleBalanceLocked.text = df.format(BigDecimal(locked).divide(APIService.SEMUXMULTIPLICATOR))
        createQR()

    }

    fun onImageClick(view: View) {
        val intent = Intent(this, QrViewActivity::class.java)
        intent.putExtra("address", address)
        startActivity(intent)
    }

    fun onSendClick(view: View) {
        val intent = Intent(this, SendActivity::class.java)

        intent.putExtra("address", address)
        intent.putExtra("available", available)
        intent.putExtra("locked", locked)

        startActivity(intent)
    }

    private fun createQR() {
        val multiFormatWriter = MultiFormatWriter()

        val bitMatrix = multiFormatWriter.encode(address, BarcodeFormat.QR_CODE, 200, 200)
        val barcodeEncoder = BarcodeEncoder()
        val bitmap = barcodeEncoder.createBitmap(bitMatrix)

        qrAddressImageView.setImageBitmap(bitmap)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        when (item.itemId) {
            R.id.balancesMenu -> balanceActivity(this)
            R.id.createAccout -> createActivity(this)

        }
        return super.onOptionsItemSelected(item)
    }

}
