package com.penumbraos.sdkexample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.api.HttpMethod
import com.penumbraos.sdkexample.ui.theme.SDKExampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

//    private var networkService: INetworkService? = null
//    private var isServiceBound by mutableStateOf(false)
//    private var fetchedData by mutableStateOf("No data yet")
//    private var serviceConnectionStatus by mutableStateOf("Service not connected")

//    private val serviceConnection = object : ServiceConnection {
//        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
//            networkService = INetworkService.Stub.asInterface(service)
//            isServiceBound = true
//            serviceConnectionStatus = "Service connected"
//            Log.w("MainActivity", "Service connected")
//        }
//
//        override fun onServiceDisconnected(name: ComponentName?) {
//            networkService = null
//            isServiceBound = false
//            serviceConnectionStatus = "Service disconnected"
//            Log.w("MainActivity", "Service disconnected")
//        }
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SDKExampleTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
//                    NetworkClientScreen(
//                        modifier = Modifier.padding(innerPadding),
//                        serviceBound = isServiceBound,
//                        fetchedData = fetchedData,
//                        serviceStatus = serviceConnectionStatus,
//                        onFetchClick = {
//                            if (isServiceBound) {
//                                CoroutineScope(Dispatchers.IO).launch {
//                                    try {
//                                        val result = networkService?.fetchUrl("https://jsonplaceholder.typicode.com/todos/1") ?: "Service not bound or returned null"
//                                        withContext(Dispatchers.Main) {
//                                            fetchedData = result
//                                        }
//                                        Log.w("MainActivity", "Fetch result: $result")
//                                    } catch (e: Exception) {
//                                        Log.e("MainActivity", "Error fetching URL", e)
//                                        withContext(Dispatchers.Main) {
//                                            fetchedData = "Error: ${e.message}"
//                                        }
//                                    }
//                                }
//                            } else {
//                                fetchedData = "Service not bound. Cannot fetch."
//                                Log.w("MainActivity", "Fetch clicked but service not bound.")
//                            }
//                        }
//                    )
//                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
//        Intent("com.penumbraos.bridge.INetworkService").also { intent ->
//            // The bridge app (com.penumbraos.bridge) must be installed for this to work.
//            // And it must be running as system user if it's to have special permissions.
//            intent.`package` = "com.penumbraos.bridge" // Explicitly specify the package
//        try {
//            val socket = java.net.Socket("127.0.0.1", 4444) // Connect to localhost:8080
//            Log.w("MainActivity", "Connected to socket")
//            socket.outputStream.write("hello world".toByteArray())
//            socket.close()
//        } catch (e: Exception) {
//            Log.e("MainActivity", "Error connecting to socket", e)
//        }

        try {
            val client = PenumbraClient()
            CoroutineScope(Dispatchers.IO).launch {
                val response = client.http.request("https://example.com", HttpMethod.GET)
                Log.w("MainActivity", "Response: $response")
            }
        } catch (e: SecurityException) {
//                serviceConnectionStatus = "SecurityException: Cannot bind to service. Check permissions and SELinux."
            Log.e("MainActivity", "SecurityException binding to service", e)
        } catch (e: Exception) {
//                serviceConnectionStatus = "Exception binding to service: ${e.message}"
            Log.e("MainActivity", "General Exception binding to service", e)
        }
    }
//    }

    override fun onStop() {
        super.onStop()
//        if (isServiceBound) {
////            unbindService(serviceConnection)
//            isServiceBound = false
//            networkService = null
//            serviceConnectionStatus = "Service unbound in onStop"
//            Log.w("MainActivity", "Service unbound")
//        }
    }
}
