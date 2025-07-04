package com.penumbraos.sdkexample

import android.os.Bundle
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.penumbraos.sdk.PenumbraClient
import com.penumbraos.sdk.api.HttpMethod
import com.penumbraos.sdk.api.types.LedAnimation
import com.penumbraos.sdk.api.types.TouchpadInputReceiver
import com.penumbraos.sdkexample.ui.theme.SDKExampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    lateinit var client: PenumbraClient

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
        try {
            client = PenumbraClient(applicationContext, true)
        } catch (e: SecurityException) {
//                serviceConnectionStatus = "SecurityException: Cannot bind to service. Check permissions and SELinux."
            Log.e("MainActivity", "SecurityException binding to service", e)
        } catch (e: Exception) {
//                serviceConnectionStatus = "Exception binding to service: ${e.message}"
            Log.e("MainActivity", "General Exception binding to service", e)
        }

        CoroutineScope(Dispatchers.IO).launch {
            client.waitForBridge()
            // Hack to start STT service in advance of usage
//            client.stt.launchListenerProcess(applicationContext)

            var currentIndex = 31

            client.touchpad.register(object : TouchpadInputReceiver {
                override fun onInputEvent(event: InputEvent) {
                    val event = event as MotionEvent
                    if (event.action == MotionEvent.ACTION_UP && event.eventTime - event.downTime < 200) {
//                        client.led.clearAllAnimation()
                        currentIndex += 1
                        if (currentIndex > 31) {
                            currentIndex = 0
                        }

                        val animation = LedAnimation.fromValue(currentIndex)
                        if (animation != null) {
                            client.led.playAnimation(animation)
                            Log.w("MainActivity", "LED Animation: $currentIndex")
                        } else {
                            Log.w("MainActivity", "LED Animation: Skipping $currentIndex")
                        }
                    }
                }
            })
//            client.stt.initialize(object : SttRecognitionListener() {
//                override fun onError(error: Int) {
//                    Log.w("MainActivity", "STT Error: $error")
//                }
//
//                override fun onResults(results: Bundle?) {
//                    val lines = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
//                    Log.w("MainActivity", "STT Results: $lines")
//                }
//
//                override fun onPartialResults(partialResults: Bundle?) {
//                    val lines =
//                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
//                    Log.w("MainActivity", "STT Partial Results: $lines")
//                }
//            })
//
//            client.stt.startListening()
//            makeRequest()
        }
    }
//    }

    fun makeRequest() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.http.request("https://example.com", HttpMethod.GET)
                Log.w("MainActivity", "Response: $response")
            } catch (e: Exception) {
                Log.e("MainActivity", "General Exception", e)
            }
        }
    }

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
