package com.penumbraos.sdkexample

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
//import com.penumbraos.bridge.INetworkService
import com.penumbraos.sdkexample.ui.theme.SDKExampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

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
//                val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                val serviceManager = Class.forName("android.os.ServiceManager")
                val getService = serviceManager.getMethod("getService", String::class.java)

                val binder = getService.invoke(null, "nfc") as IBinder
//                networkService = INetworkService.Stub.asInterface(binder)
////                val method = serviceManager.getMethod("listServices")
////                val serviceInfo = method.invoke(null) as? Array<*>
////                Log.w("MainActivity", "Service info: ${serviceInfo.contentToString()}")
//
//                isServiceBound = networkService != null
//                Log.w("MainActivity", "bindService called, result: $networkService")
//                if (networkService == null) {
//                    serviceConnectionStatus = "Failed to bind service. Is the bridge app installed and running?"
//                    Log.e("MainActivity", "Failed to bind service. Check if com.penumbraos.bridge is installed and the service is declared correctly.")
//                }
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

@Composable
fun NetworkClientScreen(
    modifier: Modifier = Modifier,
    serviceBound: Boolean,
    fetchedData: String,
    serviceStatus: String,
    onFetchClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Service Status: $serviceStatus")
        Button(onClick = onFetchClick, enabled = serviceBound) {
            Text("Fetch Data from URL")
        }
        Text(
            text = "Fetched Data:",
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = fetchedData,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NetworkClientScreenPreview() {
    SDKExampleTheme {
        NetworkClientScreen(
            serviceBound = true,
            fetchedData = "Sample fetched data from preview.",
            serviceStatus = "Service Connected (Preview)",
            onFetchClick = {}
        )
    }
}