// Example usage of SerialUtil for Landi M20se
// Add this code to your Activity or Fragment

import android.content.Context
import android.util.Log
import com.example.webviewapp.SerialUtil

class SerialTestExample {
    
    fun testSerialMethods(context: Context) {
        Log.d("SerialTest", "=== Testing Serial Number Methods ===")

        // Method 1: Get serial using optimized logic
        val currentSerial = SerialUtil.getDeviceSerial(context)
        Log.d("SerialTest", "getDeviceSerial() result: $currentSerial")

        // Method 2: Test a common vendor property method
        val propSerial = SerialUtil.getSerialByMethod(context, "sys_product_sn")
        Log.d("SerialTest", "sys_product_sn: $propSerial")

        // Method 3: Test all methods for comparison
        val allResults = SerialUtil.testAllMethods(context)
        Log.d("SerialTest", "All methods results:")
        allResults.forEach { (method, result) ->
            Log.d("SerialTest", "  $method: $result")
        }

        // No hard-coded expected value here; inspect logs or assert in tests as needed
    }
    
    // Call this method in your Activity onCreate or wherever appropriate
    fun runTest(context: Context) {
        try {
            testSerialMethods(context)
        } catch (e: Exception) {
            Log.e("SerialTest", "Error during serial testing: ${e.message}", e)
        }
    }
}

/*
Usage example in Activity:

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Test serial methods
        val tester = SerialTestExample()
        tester.runTest(this)
        
        // Or use directly:
        val serial = SerialUtil.getDeviceSerial(this)
        Log.d("MainActivity", "Device serial: $serial")
    }
}

Expected Results for Landi M20se:
- sys_product_sn: 247ACD201002
- getDeviceSerial(): 247ACD201002
*/
